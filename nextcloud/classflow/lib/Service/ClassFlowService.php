<?php

declare(strict_types=1);

namespace OCA\ClassFlow\Service;

use OCP\DB\QueryBuilder\IQueryBuilder;
use OCP\IDBConnection;

final class ClassFlowService {
    private const TYPES = ['course', 'slot', 'agenda', 'study'];
    private const OPERATIONS = ['upsert', 'delete'];
    private const AGENDA_TYPES = ['homework', 'exam', 'activity', 'other'];
    private const AGENDA_STATUSES = ['pending', 'completed'];

    public function __construct(private IDBConnection $db) {
    }

    /** @return array<string, mixed> */
    public function state(string $userId): array {
        $courses = array_map(fn (array $row): array => $this->courseResponse($row), $this->rows('classflow_courses', $userId, ['name' => 'ASC']));
        $slots = array_map(fn (array $row): array => $this->slotResponse($row), $this->rows('classflow_slots', $userId, ['day_of_week' => 'ASC', 'start_minutes' => 'ASC']));
        $agendaRows = $this->rows('classflow_agenda', $userId, ['occurs_at' => 'ASC']);
        $links = $this->linksByAgenda($userId);
        $agenda = array_map(
            fn (array $row): array => $this->agendaResponse($row, $links[(string)$row['uuid']] ?? []),
            $agendaRows,
        );

        return [
            'courses' => $courses,
            'slots' => $slots,
            'agendaItems' => $agenda,
            'studyPlans' => array_map(fn (array $row): array => $this->studyResponse($row), $this->rows('classflow_studies', $userId, ['starts_at' => 'ASC'])),
            'serverTime' => (int)round(microtime(true) * 1000),
        ];
    }

    /**
     * @param list<array<string, mixed>> $mutations
     * @return array<string, mixed>
     */
    public function sync(string $userId, array $mutations): array {
        if (count($mutations) > 200) {
            throw new \InvalidArgumentException('A sync batch can contain at most 200 mutations');
        }

        $accepted = [];
        $conflicts = [];
        $this->db->beginTransaction();
        try {
            foreach ($mutations as $mutation) {
                $operationId = $this->requiredString($mutation, 'operationId', 36);
                if ($this->operationProcessed($userId, $operationId)) {
                    $accepted[] = $operationId;
                    continue;
                }

                try {
                    $entityType = $this->enum($mutation, 'entityType', self::TYPES);
                    $operation = $this->enum($mutation, 'operation', self::OPERATIONS);
                    $entityId = $this->uuid($mutation, 'entityId');
                    $baseVersion = max(0, (int)($mutation['baseVersion'] ?? 0));
                    $current = $this->findEntity($userId, $entityType, $entityId);

                    if ($operation === 'delete') {
                        if ($current !== null && (int)$current['version'] !== $baseVersion) {
                            $conflicts[] = $this->conflict($operationId, $entityType, $current, $userId);
                            continue;
                        }
                        if ($current !== null) {
                            $this->deleteEntity($userId, $entityType, $entityId);
                        }
                    } else {
                        if (($current === null && $baseVersion !== 0) || ($current !== null && (int)$current['version'] !== $baseVersion)) {
                            $conflicts[] = $this->conflict($operationId, $entityType, $current, $userId);
                            continue;
                        }
                        $payload = $mutation['payload'] ?? null;
                        if (!is_array($payload)) {
                            throw new \InvalidArgumentException('Mutation payload must be an object');
                        }
                        if (($payload['id'] ?? null) !== $entityId) {
                            throw new \InvalidArgumentException('Payload id does not match entityId');
                        }
                        $this->upsertEntity($userId, $entityType, $entityId, $payload, $current);
                    }

                    $this->rememberOperation($userId, $operationId);
                    $accepted[] = $operationId;
                } catch (\InvalidArgumentException $error) {
                    $conflicts[] = [
                        'operationId' => $operationId,
                        'server' => null,
                        'error' => $error->getMessage(),
                    ];
                }
            }
            $this->db->commit();
        } catch (\Throwable $error) {
            $this->db->rollBack();
            throw $error;
        }

        return [
            'state' => $this->state($userId),
            'acceptedOperationIds' => array_values(array_unique($accepted)),
            'conflicts' => $conflicts,
        ];
    }

    /** @return list<array<string, mixed>> */
    private function rows(string $table, string $userId, array $order = []): array {
        $qb = $this->db->getQueryBuilder();
        $qb->select('*')
            ->from($table)
            ->where($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)));
        foreach ($order as $column => $direction) {
            $qb->addOrderBy($column, $direction);
        }
        $result = $qb->executeQuery();
        $rows = $result->fetchAllAssociative();
        $result->closeCursor();
        return $rows;
    }

    /** @return array<string, list<string>> */
    private function linksByAgenda(string $userId): array {
        $links = [];
        foreach ($this->rows('classflow_links', $userId) as $row) {
            $links[(string)$row['agenda_uuid']][] = (string)$row['slot_uuid'];
        }
        return $links;
    }

    /** @return array<string, mixed>|null */
    private function findEntity(string $userId, string $type, string $uuid): ?array {
        $table = $this->tableFor($type);
        $qb = $this->db->getQueryBuilder();
        $qb->select('*')
            ->from($table)
            ->where($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
            ->andWhere($qb->expr()->eq('uuid', $qb->createNamedParameter($uuid)))
            ->setMaxResults(1);
        $result = $qb->executeQuery();
        $row = $result->fetchAssociative();
        $result->closeCursor();
        return $row === false ? null : $row;
    }

    /** @param array<string, mixed> $payload @param array<string, mixed>|null $current */
    private function upsertEntity(string $userId, string $type, string $uuid, array $payload, ?array $current): void {
        $agendaSlotIds = $type === 'agenda'
            ? $this->validateAgendaLinks($userId, $payload['linkedSlotIds'] ?? [])
            : [];
        $now = (int)round(microtime(true) * 1000);
        $version = $current === null ? 1 : (int)$current['version'] + 1;
        $values = match ($type) {
            'course' => $this->courseValues($payload),
            'slot' => $this->slotValues($userId, $payload),
            'agenda' => $this->agendaValues($payload),
            'study' => $this->studyValues($userId, $payload, $current),
        };
        $values['version'] = $version;
        $values['updated_at'] = $now;

        if ($current === null) {
            $values['user_id'] = $userId;
            $values['uuid'] = $uuid;
            $qb = $this->db->getQueryBuilder();
            $qb->insert($this->tableFor($type));
            foreach ($values as $column => $value) {
                $qb->setValue($column, $qb->createNamedParameter($value));
            }
            $qb->executeStatement();
        } else {
            $qb = $this->db->getQueryBuilder();
            $qb->update($this->tableFor($type))
                ->where($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
                ->andWhere($qb->expr()->eq('uuid', $qb->createNamedParameter($uuid)));
            foreach ($values as $column => $value) {
                $qb->set($column, $qb->createNamedParameter($value));
            }
            $qb->executeStatement();
        }

        if ($type === 'agenda') {
            $this->replaceAgendaLinks($userId, $uuid, $agendaSlotIds);
        }
    }

    /** @param array<string, mixed> $payload @return array<string, mixed> */
    private function courseValues(array $payload): array {
        return [
            'name' => $this->requiredString($payload, 'name', 160),
            'teacher' => $this->optionalString($payload, 'teacher', 160),
            'room' => $this->optionalString($payload, 'room', 120),
            'color_key' => max(0, min(32767, (int)($payload['colorKey'] ?? 0))),
            'notes' => $this->optionalString($payload, 'notes', 5000),
        ];
    }

    /** @param array<string, mixed> $payload @return array<string, mixed> */
    private function slotValues(string $userId, array $payload): array {
        $courseId = $this->uuid($payload, 'courseId');
        if ($this->findEntity($userId, 'course', $courseId) === null) {
            throw new \InvalidArgumentException('Course does not exist');
        }
        $day = (int)($payload['dayOfWeek'] ?? 0);
        $start = (int)($payload['startMinutes'] ?? -1);
        $end = (int)($payload['endMinutes'] ?? -1);
        if ($day < 1 || $day > 7 || $start < 0 || $end > 1440 || $start >= $end) {
            throw new \InvalidArgumentException('Invalid timetable range');
        }
        return [
            'course_uuid' => $courseId,
            'day_of_week' => $day,
            'start_minutes' => $start,
            'end_minutes' => $end,
            'room_override' => $this->optionalString($payload, 'roomOverride', 120),
        ];
    }

    /** @param array<string, mixed> $payload @return array<string, mixed> */
    private function agendaValues(array $payload): array {
        $type = $this->enum($payload, 'type', self::AGENDA_TYPES);
        $status = $this->enum($payload, 'status', self::AGENDA_STATUSES);
        $occursAt = (int)($payload['occursAt'] ?? 0);
        $endsAt = isset($payload['endsAt']) ? (int)$payload['endsAt'] : null;
        if ($occursAt <= 0 || ($endsAt !== null && $endsAt < $occursAt)) {
            throw new \InvalidArgumentException('Invalid agenda date range');
        }
        return [
            'type' => $type,
            'title' => $this->requiredString($payload, 'title', 255),
            'occurs_at' => $occursAt,
            'ends_at' => $endsAt,
            'all_day' => !empty($payload['allDay']) ? 1 : 0,
            'status' => $status,
            'notes' => $this->optionalString($payload, 'notes', 10000),
            'reminder_at' => isset($payload['reminderAt']) ? (int)$payload['reminderAt'] : null,
        ];
    }

    /** @param array<string, mixed> $payload @param array<string, mixed>|null $current @return array<string, mixed> */
    private function studyValues(string $userId, array $payload, ?array $current): array {
        $startsAt = (int)($payload['startsAt'] ?? 0);
        $endsAt = (int)($payload['endsAt'] ?? 0);
        if ($startsAt <= 0 || $endsAt <= $startsAt) {
            throw new \InvalidArgumentException('Invalid study date range');
        }
        $courseId = isset($payload['linkedCourseId']) ? $this->uuid($payload, 'linkedCourseId') : null;
        $agendaId = isset($payload['linkedAgendaId']) ? $this->uuid($payload, 'linkedAgendaId') : null;
        if ($courseId !== null && $agendaId !== null) {
            throw new \InvalidArgumentException('A study plan can link to only one target');
        }
        if ($courseId !== null && $this->findEntity($userId, 'course', $courseId) === null && ($current['course_uuid'] ?? null) !== $courseId) {
            throw new \InvalidArgumentException('Study link does not exist');
        }
        if ($agendaId !== null) {
            $agenda = $this->findEntity($userId, 'agenda', $agendaId);
            if ($agenda === null && ($current['agenda_uuid'] ?? null) !== $agendaId) {
                throw new \InvalidArgumentException('Study link does not exist');
            }
            if ($agenda !== null && !in_array($agenda['type'], ['homework', 'exam'], true)) {
                throw new \InvalidArgumentException('Study links must be homework or exam');
            }
        }
        return [
            'title' => $this->requiredString($payload, 'title', 255),
            'starts_at' => $startsAt,
            'ends_at' => $endsAt,
            'course_uuid' => $courseId,
            'agenda_uuid' => $agendaId,
            'notes' => $this->optionalString($payload, 'notes', 10000),
        ];
    }

    /** @param mixed $slotIds @return list<string> */
    private function validateAgendaLinks(string $userId, mixed $slotIds): array {
        if (!is_array($slotIds) || count($slotIds) > 40) {
            throw new \InvalidArgumentException('linkedSlotIds must be an array');
        }

        $validated = [];
        foreach (array_values(array_unique($slotIds)) as $slotId) {
            if (!is_string($slotId) || $this->findEntity($userId, 'slot', $slotId) === null) {
                throw new \InvalidArgumentException('Linked slot does not exist');
            }
            $validated[] = $slotId;
        }
        return $validated;
    }

    /** @param list<string> $slotIds */
    private function replaceAgendaLinks(string $userId, string $agendaId, array $slotIds): void {
        $delete = $this->db->getQueryBuilder();
        $delete->delete('classflow_links')
            ->where($delete->expr()->eq('user_id', $delete->createNamedParameter($userId)))
            ->andWhere($delete->expr()->eq('agenda_uuid', $delete->createNamedParameter($agendaId)))
            ->executeStatement();

        foreach ($slotIds as $slotId) {
            $insert = $this->db->getQueryBuilder();
            $insert->insert('classflow_links')
                ->values([
                    'user_id' => $insert->createNamedParameter($userId),
                    'agenda_uuid' => $insert->createNamedParameter($agendaId),
                    'slot_uuid' => $insert->createNamedParameter($slotId),
                ])
                ->executeStatement();
        }
    }

    private function deleteEntity(string $userId, string $type, string $uuid): void {
        if ($type === 'course') {
            $qb = $this->db->getQueryBuilder();
            $qb->select('uuid')->from('classflow_slots')
                ->where($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
                ->andWhere($qb->expr()->eq('course_uuid', $qb->createNamedParameter($uuid)));
            $result = $qb->executeQuery();
            $slotIds = array_column($result->fetchAllAssociative(), 'uuid');
            $result->closeCursor();
            foreach ($slotIds as $slotId) {
                $this->deleteLinksBySlot($userId, (string)$slotId);
            }
            $this->deleteWhere('classflow_slots', $userId, 'course_uuid', $uuid);
        } elseif ($type === 'slot') {
            $this->deleteLinksBySlot($userId, $uuid);
        } elseif ($type === 'agenda') {
            $this->deleteWhere('classflow_links', $userId, 'agenda_uuid', $uuid);
        }
        $this->deleteWhere($this->tableFor($type), $userId, 'uuid', $uuid);
    }

    private function deleteLinksBySlot(string $userId, string $slotId): void {
        $this->deleteWhere('classflow_links', $userId, 'slot_uuid', $slotId);
    }

    private function deleteWhere(string $table, string $userId, string $column, string $value): void {
        $qb = $this->db->getQueryBuilder();
        $qb->delete($table)
            ->where($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
            ->andWhere($qb->expr()->eq($column, $qb->createNamedParameter($value)))
            ->executeStatement();
    }

    private function operationProcessed(string $userId, string $operationId): bool {
        $qb = $this->db->getQueryBuilder();
        $qb->select('id')->from('classflow_ops')
            ->where($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
            ->andWhere($qb->expr()->eq('operation_id', $qb->createNamedParameter($operationId)))
            ->setMaxResults(1);
        $result = $qb->executeQuery();
        $found = $result->fetchOne() !== false;
        $result->closeCursor();
        return $found;
    }

    private function rememberOperation(string $userId, string $operationId): void {
        $qb = $this->db->getQueryBuilder();
        $qb->insert('classflow_ops')->values([
            'user_id' => $qb->createNamedParameter($userId),
            'operation_id' => $qb->createNamedParameter($operationId),
            'created_at' => $qb->createNamedParameter((int)round(microtime(true) * 1000), IQueryBuilder::PARAM_INT),
        ])->executeStatement();
    }

    /** @param array<string, mixed>|null $current @return array<string, mixed> */
    private function conflict(string $operationId, string $type, ?array $current, string $userId): array {
        $server = null;
        if ($current !== null) {
            $server = match ($type) {
                'course' => $this->courseResponse($current),
                'slot' => $this->slotResponse($current),
                'agenda' => $this->agendaResponse($current, $this->linksByAgenda($userId)[(string)$current['uuid']] ?? []),
                'study' => $this->studyResponse($current),
            };
        }
        return ['operationId' => $operationId, 'server' => $server];
    }

    private function tableFor(string $type): string {
        return match ($type) {
            'course' => 'classflow_courses',
            'slot' => 'classflow_slots',
            'agenda' => 'classflow_agenda',
            'study' => 'classflow_studies',
            default => throw new \InvalidArgumentException('Unknown entity type'),
        };
    }

    /** @param array<string, mixed> $row @return array<string, mixed> */
    private function courseResponse(array $row): array {
        return [
            'id' => (string)$row['uuid'],
            'name' => (string)$row['name'],
            'teacher' => (string)($row['teacher'] ?? ''),
            'room' => (string)($row['room'] ?? ''),
            'colorKey' => (int)$row['color_key'],
            'notes' => (string)($row['notes'] ?? ''),
            'version' => (int)$row['version'],
            'updatedAt' => (int)$row['updated_at'],
        ];
    }

    /** @param array<string, mixed> $row @return array<string, mixed> */
    private function slotResponse(array $row): array {
        return [
            'id' => (string)$row['uuid'],
            'courseId' => (string)$row['course_uuid'],
            'dayOfWeek' => (int)$row['day_of_week'],
            'startMinutes' => (int)$row['start_minutes'],
            'endMinutes' => (int)$row['end_minutes'],
            'roomOverride' => (string)($row['room_override'] ?? ''),
            'version' => (int)$row['version'],
            'updatedAt' => (int)$row['updated_at'],
        ];
    }

    /** @param array<string, mixed> $row @param list<string> $slotIds @return array<string, mixed> */
    private function agendaResponse(array $row, array $slotIds): array {
        return [
            'id' => (string)$row['uuid'],
            'type' => (string)$row['type'],
            'title' => (string)$row['title'],
            'occursAt' => (int)$row['occurs_at'],
            'endsAt' => $row['ends_at'] === null ? null : (int)$row['ends_at'],
            'allDay' => (bool)$row['all_day'],
            'status' => (string)$row['status'],
            'notes' => (string)($row['notes'] ?? ''),
            'reminderAt' => $row['reminder_at'] === null ? null : (int)$row['reminder_at'],
            'linkedSlotIds' => array_values($slotIds),
            'version' => (int)$row['version'],
            'updatedAt' => (int)$row['updated_at'],
        ];
    }

    /** @param array<string, mixed> $data */
    private function requiredString(array $data, string $key, int $maxLength): string {
        $value = trim((string)($data[$key] ?? ''));
        if ($value === '' || mb_strlen($value) > $maxLength) {
            throw new \InvalidArgumentException("Invalid $key");
        }
        return $value;
    }

    /** @param array<string, mixed> $row @return array<string, mixed> */
    private function studyResponse(array $row): array {
        return [
            'id' => (string)$row['uuid'],
            'title' => (string)$row['title'],
            'startsAt' => (int)$row['starts_at'],
            'endsAt' => (int)$row['ends_at'],
            'linkedCourseId' => $row['course_uuid'] === null ? null : (string)$row['course_uuid'],
            'linkedAgendaId' => $row['agenda_uuid'] === null ? null : (string)$row['agenda_uuid'],
            'notes' => (string)($row['notes'] ?? ''),
            'version' => (int)$row['version'],
            'updatedAt' => (int)$row['updated_at'],
        ];
    }

    /** @param array<string, mixed> $data */
    private function optionalString(array $data, string $key, int $maxLength): ?string {
        $value = trim((string)($data[$key] ?? ''));
        if (mb_strlen($value) > $maxLength) {
            throw new \InvalidArgumentException("Invalid $key");
        }
        return $value === '' ? null : $value;
    }

    /** @param array<string, mixed> $data */
    private function uuid(array $data, string $key): string {
        $value = strtolower($this->requiredString($data, $key, 36));
        if (preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/', $value) !== 1) {
            throw new \InvalidArgumentException("Invalid $key");
        }
        return $value;
    }

    /** @param array<string, mixed> $data @param list<string> $allowed */
    private function enum(array $data, string $key, array $allowed): string {
        $value = strtolower((string)($data[$key] ?? ''));
        if (!in_array($value, $allowed, true)) {
            throw new \InvalidArgumentException("Invalid $key");
        }
        return $value;
    }
}
