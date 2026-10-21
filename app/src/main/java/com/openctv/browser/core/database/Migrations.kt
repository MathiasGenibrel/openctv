package com.openctv.browser.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 → 2 : ajout de l'historique. Les favoris ne sont pas touchés (aucune migration destructive : une base
 * sans migration prévue fait échouer l'ouverture plutôt que d'effacer les favoris).
 * Le SQL reproduit exactement le schéma exporté par Room (`app/schemas/…/2.json`), vérifié par `MigrationSchemaTest`.
 */
val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `history_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `visitedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_entries_visitedAt` ON `history_entries` (`visitedAt`)")
    }
}

/** Toutes les migrations, de la plus ancienne à la plus récente : une par version, sans trou (cf. `MigrationSchemaTest`). */
val AllMigrations: List<Migration> = listOf(Migration1To2)
