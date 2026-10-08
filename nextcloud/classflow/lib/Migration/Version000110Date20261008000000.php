<?php

declare(strict_types=1);

namespace OCA\ClassFlow\Migration;

use Closure;
use OCP\DB\ISchemaWrapper;
use OCP\DB\Types;
use OCP\Migration\IOutput;
use OCP\Migration\SimpleMigrationStep;

final class Version000110Date20261008000000 extends SimpleMigrationStep {
    public function changeSchema(IOutput $output, Closure $schemaClosure, array $options): ?ISchemaWrapper {
        /** @var ISchemaWrapper $schema */
        $schema = $schemaClosure();
        if (!$schema->hasTable('classflow_studies')) {
            $table = $schema->createTable('classflow_studies');
            $table->addColumn('id', Types::BIGINT, ['autoincrement' => true, 'notnull' => true, 'unsigned' => true]);
            $table->addColumn('user_id', Types::STRING, ['notnull' => true, 'length' => 64]);
            $table->addColumn('uuid', Types::STRING, ['notnull' => true, 'length' => 36]);
            $table->addColumn('title', Types::STRING, ['notnull' => true, 'length' => 255]);
            $table->addColumn('starts_at', Types::BIGINT, ['notnull' => true]);
            $table->addColumn('ends_at', Types::BIGINT, ['notnull' => true]);
            // Soft links retain plans when their original course or agenda is deleted.
            $table->addColumn('course_uuid', Types::STRING, ['notnull' => false, 'length' => 36]);
            $table->addColumn('agenda_uuid', Types::STRING, ['notnull' => false, 'length' => 36]);
            $table->addColumn('notes', Types::TEXT, ['notnull' => false]);
            $table->addColumn('version', Types::BIGINT, ['notnull' => true, 'default' => 1]);
            $table->addColumn('updated_at', Types::BIGINT, ['notnull' => true]);
            $table->setPrimaryKey(['id'], 'cf_studies_pk');
            $table->addUniqueIndex(['user_id', 'uuid'], 'cf_studies_user_uuid');
            $table->addIndex(['user_id', 'starts_at'], 'cf_studies_date');
        }
        return $schema;
    }
}
