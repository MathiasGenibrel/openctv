package com.openctv.browser.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Test

/**
 * Migrations Room vérifiées sur la JVM, contre les schémas exportés par Room (`app/schemas/`) : appliquer la migration
 * n → n+1 au schéma n doit donner exactement le schéma n+1 (tables et index). Sans appareil ni dépendance de test
 * supplémentaire ; la validation complète de Room à l'ouverture reste à faire sur la TV.
 */
class MigrationSchemaTest {

    private val schemaDir = File("schemas/com.openctv.browser.core.database.OpenCtvDatabase")

    private val versions: List<Int> = schemaDir.listFiles().orEmpty()
        .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
        .sorted()

    /** `CREATE …` attendus par Room pour une version : tables et index, `${TABLE_NAME}` résolu. */
    private fun schemaStatements(version: Int): Set<String> {
        val json = File(schemaDir, "$version.json").readText()
        return json.split("\"tableName\": \"").drop(1).flatMap { entity ->
            val table = entity.substringBefore('"')
            CreateSql.findAll(entity).map { it.groupValues[1].replace("\${TABLE_NAME}", table) }.toList()
        }.toSet()
    }

    private fun executedBy(from: Int): List<String> {
        val migration = AllMigrations.single { it.startVersion == from }
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        migration.migrate(db)
        return statements
    }

    @Test
    fun `Given every exported schema When listing the migrations Then each version has exactly one migration to the next`() {
        assertThat(versions).isNotEmpty()
        assertThat(versions).isEqualTo((versions.first()..versions.last()).toList())

        val steps = AllMigrations.map { it.startVersion to it.endVersion }
        val expected = versions.zipWithNext()
        assertThat(steps).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `Given a TV on any older schema When the app migrates it Then the database matches exactly the next schema`() {
        versions.zipWithNext().forEach { (from, to) ->
            val migrated = schemaStatements(from) + executedBy(from)

            assertThat(migrated).isEqualTo(schemaStatements(to))
        }
    }

    @Test
    fun `Given bookmarks saved in version 1 When migrating to version 2 Then the bookmarks table is left untouched`() {
        val statements = executedBy(from = 1)

        val touchesBookmarks = statements.filter { "`bookmarks`" in it }
        val destructive = statements.filter { statement ->
            listOf("DROP", "DELETE", "ALTER", "UPDATE").any { statement.trimStart().startsWith(it, ignoreCase = true) }
        }
        assertThat(touchesBookmarks).isEmpty()
        assertThat(destructive).isEmpty()
        assertThat(schemaStatements(1).filter { "`bookmarks`" in it })
            .containsExactlyElementsIn(schemaStatements(2).filter { "`bookmarks`" in it })
    }

    private companion object {
        val CreateSql = Regex("\"createSql\": \"([^\"]*)\"")
    }
}
