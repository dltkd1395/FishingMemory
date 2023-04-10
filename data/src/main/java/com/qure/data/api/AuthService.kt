package com.qure.data.api

import com.qure.data.entity.auth.SignUpUserEntity
import com.qure.domain.entity.auth.SignUpFields
import com.qure.domain.entity.auth.SignUpFieldsEntity
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthService {

    @POST("/v1beta1/projects/{projectId}/databases/(default)/documents/auth")
    suspend fun postSignUp(
        @Path("projectId") projectId: String,
        @Query("documentId") email: String,
        @Body fields: SignUpFieldsEntity,
    ): Result<SignUpUserEntity>

    @GET("/v1beta1/projects/{projectId}/databases/(default)/documents/auth/{documentId}")
    suspend fun getUserInfo(
        @Path("projectId") projectId: String,
        @Path("documentId") email: String,
    ): Result<SignUpUserEntity>
}