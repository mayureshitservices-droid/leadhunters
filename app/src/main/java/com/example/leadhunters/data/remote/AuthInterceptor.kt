package com.example.leadhunters.data.remote

import com.example.leadhunters.data.local.AuthPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val authPreferences: AuthPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Skip auth for registration endpoint
        if (request.url.encodedPath.contains("/api/auth/deviceregistration")) {
            return chain.proceed(request)
        }

        // Get token synchronously (blocking OkHttp thread)
        val token = runBlocking {
            authPreferences.authToken.first()
        }

        return if (token != null) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            // No token found. proceed as is, backend will return 401
            // which will be handled by UI or Authenticator
            chain.proceed(request)
        }
    }
}
