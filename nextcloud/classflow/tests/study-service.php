<?php

declare(strict_types=1);

require __DIR__ . '/../lib/Service/ClassFlowService.php';

// Test pure payload validation and serialization without a live Nextcloud database.
$reflection = new ReflectionClass(OCA\ClassFlow\Service\ClassFlowService::class);
$service = $reflection->newInstanceWithoutConstructor();
$values = $reflection->getMethod('studyValues');
$response = $reflection->getMethod('studyResponse');
$payload = ['title' => '複習', 'startsAt' => 1000, 'endsAt' => 2000, 'notes' => '手動時間'];
$valid = $values->invoke($service, 'test-user', $payload, null);
if ($valid['starts_at'] !== 1000 || $valid['ends_at'] !== 2000 || $valid['notes'] !== '手動時間') {
    throw new RuntimeException('Valid plan values were changed');
}
$cases = [
    array_replace($payload, ['endsAt' => 1000]),
    array_replace($payload, ['startsAt' => 0]),
    array_replace($payload, ['title' => '']),
    array_replace($payload, ['linkedCourseId' => 'not-a-uuid']),
    array_replace($payload, ['linkedCourseId' => '11111111-1111-4111-8111-111111111111', 'linkedAgendaId' => '22222222-2222-4222-8222-222222222222']),
];
foreach ($cases as $invalid) {
    try {
        $values->invoke($service, 'test-user', $invalid, null);
    } catch (InvalidArgumentException) {
        continue;
    }
    throw new RuntimeException('Invalid study payload was accepted');
}
$cloud = $response->invoke($service, ['uuid' => 'plan', 'title' => '複習', 'starts_at' => '1000', 'ends_at' => '2000', 'course_uuid' => null, 'agenda_uuid' => null, 'notes' => null, 'version' => '2', 'updated_at' => '3000']);
if ($cloud['startsAt'] !== 1000 || $cloud['version'] !== 2 || $cloud['linkedAgendaId'] !== null) {
    throw new RuntimeException('Study serialization failed');
}
echo "Study service: validation (6 cases) and serialization passed.\n";
