package com.voicehabit.tracker.data.remote

import com.voicehabit.tracker.core.network.NetworkEndpointPolicy
import com.voicehabit.tracker.data.remote.dto.VoiceProcessResponseDto
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

interface VoiceApiService {
    @Multipart
    @POST("api/v1/voice/process")
    suspend fun processVoiceAudio(
        @Part audio: MultipartBody.Part,
        @Part("client_current_time") clientCurrentTime: RequestBody,
        @Part("timezone") timezone: RequestBody,
        @Part("active_habits_context") activeHabitsContext: RequestBody,
        @Part("open_tasks_context") openTasksContext: RequestBody
    ): Response<VoiceProcessResponseDto>

    companion object {
        /**
         * Локальная схема разработки: 10.0.2.2 — хост-машина, видимая из эмулятора.
         * В release это единственные адреса, где cleartext-трафик разрешён network security config.
         */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

        /**
         * @param baseUrl адрес бэкенда; проверяется [NetworkEndpointPolicy] и нормализуется
         *   до схемы+хоста+завершающего слэша, как требует Retrofit.
         * @param enableHttpLog логировать тела запросов/ответов. По умолчанию выключено:
         *   в лог не должны попадать аудиозаписи, транскрипты и API-ключи.
         * @throws IllegalArgumentException если адрес не проходит политику.
         */
        fun create(
            baseUrl: String = DEFAULT_BASE_URL,
            enableHttpLog: Boolean = false
        ): VoiceApiService {
            val normalized = when (val endpoint = NetworkEndpointPolicy.evaluate(baseUrl)) {
                is NetworkEndpointPolicy.Endpoint.Allowed -> endpoint.baseUrl
                is NetworkEndpointPolicy.Endpoint.Rejected ->
                    throw IllegalArgumentException("Недопустимый адрес бэкенда: ${endpoint.reason}")
            }

            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)

            if (enableHttpLog) {
                clientBuilder.addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
            }

            return Retrofit.Builder()
                .baseUrl(normalized)
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VoiceApiService::class.java)
        }
    }
}
