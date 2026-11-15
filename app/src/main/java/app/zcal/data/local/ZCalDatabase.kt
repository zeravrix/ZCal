package app.zcal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MealEntryEntity::class,
        FoodItemEntity::class,
        DaySummaryEntity::class,
        FoodHistoryCacheEntity::class,
        WeightEntryEntity::class,
        WaterEntryEntity::class,
        SavedMealEntity::class,
        BarcodeCacheEntity::class,
        PendingScanEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ZCalDatabase : RoomDatabase() {
    abstract fun zcalDao(): ZCalDao
}
