<?php

declare(strict_types=1);

namespace OCA\ClassFlow\Migration;

use Closure;
use OCP\DB\ISchemaWrapper;
use OCP\DB\Types;
use OCP\Migration\IOutput;
use OCP\Migration\SimpleMigrationStep;

final class Version000100Date20260925000000 extends SimpleMigrationStep {
    public function changeSchema(IOutput $output, Closure $schemaClosure, array $options): ?ISchemaWrapper {
        /** @var ISchemaWrapper $schema */
        $schema = $schemaClosure();

        if (!$schema->hasTable('classflow_courses')) {
            $table = $schema->createTable('classflow_courses');
            $table->addColumn('id', Types::BIGINT, ['autoincrement' => true, 'notnull' => true, 'unsigned' => true]);
            $table->addColumn('user_id', Types::STRING, ['notnull' => true, 'length' => 64]);
            $table->addColumn('uuid', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->addColumn('name', Types::STRING, ['notnull' => true, 'length' => 160]);
            $table->addColumn('teacher', Types::STRING, ['notnull' => false, 'length' => 160]);
            $table->addColumn('room', Types::STRING, ['notnull' => false, 'length' => 120]);
            $table->addColumn('color_key', Types::SMALLINT, ['notnull' => true, 'default' => 0]);
            $table->addColumn('notes', Types::TEXT, ['notnull' => false]);
            $table->addColumn('version', Types::BIGINT, ['notnull' => true, 'default' => 1]);
            $table->addColumn('updated_at', Types::BIGINT, ['notnull' => true]);
            $table->setPrimaryKey(['id'], 'cf_courses_pk');
            $table->addUniqueIndex(['user_id', 'uuid'], 'cf_courses_user_uuid');
            $table->addIndex(['user_id'], 'cf_courses_user');
        }

        if (!$schema->hasTable('classflow_slots')) {
            $table = $schema->createTable('classflow_slots');
            $table->addColumn('id', Types::BIGINT, ['autoincrement' => true, 'notnull' => true, 'unsigned' => true]);
            $table->addColumn('user_id', Types::STRING, ['notnull' => true, 'length' => 64]);
            $table->addColumn('uuid', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->addColumn('course_uuid', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->addColumn('day_of_week', Types::SMALLINT, ['notnull' => true]);
            $table->addColumn('start_minutes', Types::SMALLINT, ['notnull' => true]);
            $table->addColumn('end_minutes', Types::SMALLINT, ['notnull' => true]);
            $table->addColumn('room_override', Types::STRING, ['notnull' => false, 'length' => 120]);
            $table->addColumn('version', Types::BIGINT, ['notnull' => true, 'default' => 1]);
            $table->addColumn('updated_at', Types::BIGINT, ['notnull' => true]);
            $table->setPrimaryKey(['id'], 'cf_slots_pk');
            $table->addUniqueIndex(['user_id', 'uuid'], 'cf_slots_user_uuid');
            $table->addIndex(['user_id', 'course_uuid'], 'cf_slots_course');
            $table->addIndex(['user_id', 'day_of_week'], 'cf_slots_day');
        }

        if (!$schema->hasTable('classflow_agenda')) {
            $table = $schema->createTable('classflow_agenda');
            $table->addColumn('id', Types::BIGINT, ['autoincrement' => true, 'notnull' => true, 'unsigned' => true]);
            $table->addColumn('user_id', Types::STRING, ['notnull' => true, 'length' => 64]);
            $table->addColumn('uuid', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->addColumn('type', Types::STRING, ['notnull' => true, 'length' => 16]);
            $table->addColumn('title', Types::STRING, ['notnull' => true, 'length' => 255]);
            $table->addColumn('occurs_at', Types::BIGINT, ['notnull' => true]);
            $table->addColumn('ends_at', Types::BIGINT, ['notnull' => false]);
            $table->addColumn('all_day', Types::BOOLEAN, ['notnull' => false, 'default' => false]);
            $table->addColumn('status', Types::STRING, ['notnull' => true, 'length' => 16, 'default' => 'pending']);
            $table->addColumn('notes', Types::TEXT, ['notnull' => false]);
            $table->addColumn('reminder_at', Types::BIGINT, ['notnull' => false]);
            $table->addColumn('version', Types::BIGINT, ['notnull' => true, 'default' => 1]);
            $table->addColumn('updated_at', Types::BIGINT, ['notnull' => true]);
            $table->setPrimaryKey(['id'], 'cf_agenda_pk');
            $table->addUniqueIndex(['user_id', 'uuid'], 'cf_agenda_user_uuid');
            $table->addIndex(['user_id', 'occurs_at'], 'cf_agenda_date');
        }

        if (!$schema->hasTable('classflow_links')) {
            $table = $schema->createTable('classflow_links');
            $table->addColumn('id', Types::BIGINT, ['autoincrement' => true, 'notnull' => true, 'unsigned' => true]);
            $table->addColumn('user_id', Types::STRING, ['notnull' => true, 'length' => 64]);
            $table->addColumn('agenda_uuid', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->addColumn('slot_uuid', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->setPrimaryKey(['id'], 'cf_links_pk');
            $table->addUniqueIndex(['user_id', 'agenda_uuid', 'slot_uuid'], 'cf_links_unique');
            $table->addIndex(['user_id', 'agenda_uuid'], 'cf_links_agenda');
        }

        if (!$schema->hasTable('classflow_ops')) {
            $table = $schema->createTable('classflow_ops');
            $table->addColumn('id', Types::BIGINT, ['autoincrement' => true, 'notnull' => true, 'unsigned' => true]);
            $table->addColumn('user_id', Types::STRING, ['notnull' => true, 'length' => 64]);
            $table->addColumn('operation_id', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->addColumn('created_at', Types::BIGINT, ['notnull' => true]);
            $table->setPrimaryKey(['id'], 'cf_ops_pk');
            $table->addUniqueIndex(['user_id', 'operation_id'], 'cf_ops_user_uuid');
        }

        return $schema;
    }
}

