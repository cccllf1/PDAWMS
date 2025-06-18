package com.yourcompany.wmssystem.pdawms

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    // 认证相关
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>
    
    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse<Any>>
    
    // 商品查询
    @GET("api/products")
    suspend fun getProducts(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 1000
    ): Response<ApiResponse<List<Product>>>
    
    @GET("api/products/code/{code}")
    suspend fun getProductByCode(@Path("code") code: String): Response<ApiResponse<Product>>
    
    @GET("api/products/external-code/{code}")
    suspend fun getProductByExternalCode(@Path("code") code: String): Response<ApiResponse<Product>>
    
    @GET("api/products/{productId}")
    suspend fun getProductById(@Path("productId") productId: String): Response<ApiResponse<Product>>
    
    // 库存查询
    @GET("api/inventory/by-location")
    suspend fun getInventoryByLocation(): Response<ApiResponse<List<LocationInfo>>>
    
    @GET("api/inventory/by-product")
    suspend fun getInventoryByProduct(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 1000,
        @Query("code") code: String? = null
    ): Response<ApiResponse<List<Product>>>
    
    // 入库操作
    @POST("api/inbound")
    suspend fun inbound(@Body request: InboundRequest): Response<ApiResponse<Any>>
    
    // 出库操作
    @POST("api/outbound")
    suspend fun outbound(@Body request: OutboundRequest): Response<ApiResponse<Any>>
    
    // 库存调整
    @POST("api/inventory/adjust")
    suspend fun adjustInventory(@Body request: InventoryAdjustRequest): Response<ApiResponse<InventoryAdjustResponse>>
    
    // 库存转移
    @POST("api/inventory/transfer")
    suspend fun transferInventory(@Body request: InventoryTransferRequest): Response<ApiResponse<InventoryTransferResponse>>
    
    // SKU外部条码管理
    @GET("api/sku/{skuCode}/external-codes")
    suspend fun getSkuExternalCodes(@Path("skuCode") skuCode: String): Response<ApiResponse<List<String>>>
    
    @POST("api/sku/{skuCode}/external-codes")
    suspend fun addSkuExternalCode(
        @Path("skuCode") skuCode: String,
        @Body externalCode: Map<String, String>
    ): Response<ApiResponse<Any>>
    
    @GET("api/sku/external/{externalCode}")
    suspend fun getSkuByExternalCode(@Path("externalCode") externalCode: String): Response<ApiResponse<SkuInfo>>
} 