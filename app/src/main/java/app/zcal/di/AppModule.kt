package app.zcal.di

import android.content.Context
import androidx.room.Room
import app.zcal.data.ai.FoodAnalyzer
import app.zcal.data.ai.MultiProviderFoodAnalyzer
import app.zcal.data.local.ZCalDao
import app.zcal.data.local.ZCalDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyzerBindingModule {
    @Binds
    @Singleton
    abstract fun bindFoodAnalyzer(impl: MultiProviderFoodAnalyzer): FoodAnalyzer
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideZCalDatabase(@ApplicationContext context: Context): ZCalDatabase {
        return Room.databaseBuilder(
            context,
            ZCalDatabase::class.java,
            "zcal_nutrition.db"
        )
            // Only the unreleased dev schemas (1, 2) may be wiped. From version 3 on, every change needs a
            // real Migration so a user's food log is never silently destroyed on update.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides
    @Singleton
    fun provideZCalDao(db: ZCalDatabase): ZCalDao = db.zcalDao()

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true
                    }
                )
            }
            // Section 6 & 7: Strict 45-second timeout with retry support
            install(HttpTimeout) {
                // Vision models with thinking enabled can take well over 45 s on a busy free tier.
                requestTimeoutMillis = 90_000L
                connectTimeoutMillis = 15_000L
                socketTimeoutMillis = 90_000L
            }
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(90, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                }
            }
        }
    }
}
