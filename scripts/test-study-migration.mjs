// SQLite-level migration regression; Android Room validation is covered by StudyPlanMigrationTest.
import { DatabaseSync } from 'node:sqlite'
import { readFileSync } from 'node:fs'
import { strict as assert } from 'node:assert'
import { test } from 'node:test'

const root = new URL('../', import.meta.url)
const schema = (version) => JSON.parse(readFileSync(new URL(`android/app/schemas/com.ray.classflow.data.db.ClassFlowDatabase/${version}.json`, root), 'utf8')).database
const migration = readFileSync(new URL('android/app/src/main/java/com/ray/classflow/data/db/ClassFlowDatabase.kt', root), 'utf8')

test('migration preserves existing data and matches the generated Room schema', () => {
  const db = new DatabaseSync(':memory:')
  try {
    for (const entity of schema(1).entities) {
      db.exec(entity.createSql.replaceAll('${TABLE_NAME}', entity.tableName))
      for (const index of entity.indices ?? []) db.exec(index.createSql.replaceAll('${TABLE_NAME}', entity.tableName))
    }
    db.exec("INSERT INTO courses VALUES ('course', 'existing', '', '', 0, '', 1, 1, 'SYNCED')")
    db.exec("INSERT INTO pending_mutations (operationId, entityType, entityId, operation, baseVersion, payload, createdAt) VALUES ('pending', 'course', 'course', 'delete', 1, NULL, 1)")
    const statements = [...migration.matchAll(/db\.execSQL\(\s*(?:"""([\s\S]*?)"""|"([^"\n]*)")\s*\)/g)].map((match) => match[1] ?? match[2])
    assert.equal(statements.length, 2)
    for (const sql of statements) db.exec(sql)
    assert.equal(db.prepare('SELECT name FROM courses').get().name, 'existing')
    assert.equal(db.prepare('SELECT operationId FROM pending_mutations').get().operationId, 'pending')
    const expected = new DatabaseSync(':memory:')
    try {
      for (const entity of schema(2).entities) {
        expected.exec(entity.createSql.replaceAll('${TABLE_NAME}', entity.tableName))
        for (const index of entity.indices ?? []) expected.exec(index.createSql.replaceAll('${TABLE_NAME}', entity.tableName))
      }
      for (const entity of schema(2).entities) {
        assert.deepEqual(db.prepare(`PRAGMA table_info(${entity.tableName})`).all(), expected.prepare(`PRAGMA table_info(${entity.tableName})`).all())
        assert.deepEqual(db.prepare(`PRAGMA index_list(${entity.tableName})`).all(), expected.prepare(`PRAGMA index_list(${entity.tableName})`).all())
      }
    } finally { expected.close() }
  } finally { db.close() }
})
