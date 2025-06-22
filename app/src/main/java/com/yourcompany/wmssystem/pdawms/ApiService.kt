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
        @Query("page_size") page_size: Int = 1000
    ): Response<ApiResponse<ProductListResponse>>
    
    @GET("api/products/code/{code}")
    suspend fun getProductByCode(@Path("code") code: String): Response<ApiResponse<Product>>
    
    @GET("api/products/sku/{sku}")
    suspend fun getProductBySku(@Path("sku") sku: String): Response<ApiResponse<Product>>
    
    @GET("api/products/external-code/{code}")
    suspend fun getProductByExternalCode(@Path("code") code: String): Response<ApiResponse<Product>>
    
    @GET("api/products/{product_id}")
    suspend fun getProductById(@Path("product_id") product_id: String): Response<ApiResponse<Product>>
    
    @GET("api/products")
    suspend fun searchProducts(
        @Query("search") query: String?,
        @Query("page") page: Int = 1,
        @Query("page_size") page_size: Int = 1000
    ): Response<ApiResponse<ProductListResponse>>
    
    // 库存查询
    @GET("api/inventory/by-location")
    suspend fun getInventoryByLocation(): Response<ApiResponse<List<LocationInfo>>>
    
    // 库位管理
    @GET("api/locations")
    suspend fun getLocations(
        @Query("page") page: Int = 1,
        @Query("page_size") page_size: Int = 1000,
        @Query("search") search: String? = null
    ): Response<ApiResponse<List<Location>>>
    
    @GET("api/locations/{id}")
    suspend fun getLocationById(@Path("id") id: String): Response<ApiResponse<Location>>
    
    @GET("api/locations/code/{location_code}")
    suspend fun getLocationByCode(@Path("location_code") location_code: String): Response<ApiResponse<Location>>
    
    // 新增库位管理API
    @POST("api/locations")
    suspend fun createLocation(@Body request: CreateLocationRequest): Response<ApiResponse<Location>>
    
    @PUT("api/locations/{id}")
    suspend fun updateLocation(@Path("id") id: String, @Body request: UpdateLocationRequest): Response<ApiResponse<Location>>
    
    @DELETE("api/locations/{id}")
    suspend fun deleteLocation(@Path("id") id: String): Response<ApiResponse<Any>>
    
    // 获取库位库存详情 - 使用正确的API端点
    @GET("api/inventory/location")
    suspend fun getLocationInventory(@Query("location_code") location_code: String? = null): Response<ApiResponse<LocationInventoryResponse>>
    
    // 入库操作
    @POST("api/inbound")
    suspend fun inbound(@Body request: InboundRequest): Response<InboundResponse>
    
    // 出库操作
    @POST("api/outbound")
    suspend fun outbound(@Body request: OutboundRequest): Response<OutboundResponse>
    
    // 库存调整
    @POST("api/inventory/adjust")
    suspend fun adjustInventory(@Body request: InventoryAdjustRequest): Response<ApiResponse<InventoryAdjustResponse>>
    
    // 库存转移
    @POST("api/inventory/transfer")
    suspend fun transferInventory(@Body request: InventoryTransferRequest): Response<ApiResponse<InventoryTransferResponse>>
    
    // SKU外部条码管理
    @GET("api/sku/{sku_code}/external-codes")
    suspend fun getSkuExternalCodes(@Path("sku_code") sku_code: String): Response<ApiResponse<List<ExternalCode>>>
    
    @POST("api/sku/{sku_code}/external-codes")
    suspend fun addExternalCode(
        @Path("sku_code") sku_code: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Any>>
    
    @HTTP(method = "DELETE", path = "api/sku/{sku_code}/external-codes/{external_code}", hasBody = true)
    suspend fun deleteExternalCode(
        @Path("sku_code") sku_code: String,
        @Path("external_code") external_code: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Any>>
    
    @GET("api/sku/external/{external_code}")
    suspend fun getSkuByExternalCode(@Path("external_code") external_code: String): Response<ApiResponse<SkuInfo>>
} 