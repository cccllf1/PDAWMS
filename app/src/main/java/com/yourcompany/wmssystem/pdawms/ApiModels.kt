package com.yourcompany.wmssystem.pdawms

import com.google.gson.annotations.SerializedName

// 通用API响应格式
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T?,
    @SerializedName("error_code") val error_code: String?,
    @SerializedName("error_message") val error_message: String?
)

// 分页信息
data class Pagination(
    @SerializedName("page") val page: Int,
    @SerializedName("page_size") val page_size: Int,
    @SerializedName("total") val total: Int
)

// 登录请求
data class LoginRequest(
    @SerializedName("user_name") val user_name: String,
    @SerializedName("password") val password: String
)

// 登录响应
data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("user_id") val user_id: String?,
    @SerializedName("user_name") val user_name: String,
    @SerializedName("role") val role: String,
    @SerializedName("is_admin") val is_admin: Boolean
)

// 商品信息 - 更新为新的API结构
data class Product(
    @SerializedName("product_id") val product_id: String?,
    @SerializedName("product_code") val product_code: String,
    @SerializedName("product_name") val product_name: String,
    @SerializedName("unit") val unit: String?,
    @SerializedName("image_path") val image_path: String?,
    @SerializedName("has_sku") val has_sku: Boolean?,
    @SerializedName("product_total_quantity") val product_total_quantity: Int?,  // 更新字段名
    @SerializedName("sku_count") val sku_count: Int?,
    @SerializedName("location_count") val location_count: Int?,
    @SerializedName("color_count") val color_count: Int?,
    @SerializedName("colors") val colors: List<ColorInfo>?,
    @SerializedName("skus") val skus: List<SkuInfo>?,
    @SerializedName("matched_sku") val matched_sku: SkuInfo?,
    @SerializedName("created_at") val created_at: String?,
    @SerializedName("updated_at") val updated_at: String?
)

// SKU信息 - 更新为新的API结构
data class SkuInfo(
    @SerializedName("sku_code") val sku_code: String,
    @SerializedName("sku_color") val sku_color: String?,
    @SerializedName("sku_size") val sku_size: String?,
    @SerializedName("image_path") val image_path: String?,
    @SerializedName("stock_quantity") val stock_quantity: Int?,
    @SerializedName("sku_total_quantity") val sku_total_quantity: Int?,  // 更新字段名
    @SerializedName("locations") val locations: List<LocationStock>?,
    @SerializedName("external_codes") val external_codes: List<String>?
)

// 颜色信息 - 更新为新的API结构
data class ColorInfo(
    @SerializedName("color") val color: String,
    @SerializedName("image_path") val image_path: String?,
    @SerializedName("sizes") val sizes: List<SkuInfo>?,
    @SerializedName("color_total_quantity") val color_total_quantity: Int?,  // 更新字段名
    @SerializedName("sku_count") val sku_count: Int?,
    @SerializedName("location_count") val location_count: Int?
)

// 库位库存信息
data class LocationStock(
    @SerializedName("location_code") val location_code: String,
    @SerializedName("stock_quantity") val stock_quantity: Int
)

// 库位信息
data class LocationInfo(
    @SerializedName("location_id") val location_id: String?,
    @SerializedName("location_code") val location_code: String,
    @SerializedName("location_name") val location_name: String?,
    @SerializedName("items") val items: List<InventoryItem>?
)

// 库存条目 - 更新为新的API结构
data class InventoryItem(
    @SerializedName("product_id") val product_id: String?,
    @SerializedName("product_code") val product_code: String,
    @SerializedName("product_name") val product_name: String?,
    @SerializedName("sku_code") val sku_code: String?,
    @SerializedName("sku_color") val sku_color: String?,
    @SerializedName("sku_size") val sku_size: String?,
    @SerializedName("stock_quantity") val stock_quantity: Int,
    @SerializedName("image_path") val image_path: String?,
    @SerializedName("unit") val unit: String?,
    @SerializedName("location_code") val location_code: String?,
    @SerializedName("batch_number") val batch_number: String?
)

// 入库请求 - 更新为新的API结构
data class InboundRequest(
    @SerializedName("sku_code") val sku_code: String,  // 主要字段
    @SerializedName("location_code") val location_code: String?,  // 可为null
    @SerializedName("stock_quantity") val stock_quantity: Int,  // 更新字段名
    @SerializedName("operator_id") val operator_id: String,  // 必需字段
    @SerializedName("batch_number") val batch_number: String?,
    @SerializedName("is_urgent") val is_urgent: Boolean?,
    @SerializedName("notes") val notes: String?
)

// 出库请求 - 更新为新的API结构
data class OutboundRequest(
    @SerializedName("sku_code") val sku_code: String,  // 主要字段
    @SerializedName("location_code") val location_code: String?,
    @SerializedName("stock_quantity") val stock_quantity: Int,  // 更新字段名
    @SerializedName("operator_id") val operator_id: String,  // 必需字段
    @SerializedName("batch_number") val batch_number: String?,
    @SerializedName("is_urgent") val is_urgent: Boolean?,
    @SerializedName("notes") val notes: String?
)

// 库存调整请求 - 更新为新的API结构
data class InventoryAdjustRequest(
    @SerializedName("sku_code") val sku_code: String,  // 主要字段
    @SerializedName("location_code") val location_code: String,
    @SerializedName("stock_quantity") val stock_quantity: Int,  // 更新字段名
    @SerializedName("operator_id") val operator_id: String,  // 必需字段
    @SerializedName("batch_number") val batch_number: String?,
    @SerializedName("is_urgent") val is_urgent: Boolean?,
    @SerializedName("notes") val notes: String?
)

// 库存转移请求 - 更新为新的API结构
data class InventoryTransferRequest(
    @SerializedName("sku_code") val sku_code: String,  // 主要字段
    @SerializedName("from_location_code") val from_location_code: String?,
    @SerializedName("to_location_code") val to_location_code: String?,
    @SerializedName("transfer_quantity") val transfer_quantity: Int,  // 更新字段名
    @SerializedName("operator_id") val operator_id: String,  // 必需字段
    @SerializedName("batch_number") val batch_number: String?,
    @SerializedName("is_urgent") val is_urgent: Boolean?,
    @SerializedName("notes") val notes: String?
)

// 库存转移响应 - 更新为新的API结构
data class InventoryTransferResponse(
    @SerializedName("sku_code") val sku_code: String,
    @SerializedName("from_location_code") val from_location_code: String,  // 更新字段名
    @SerializedName("to_location_code") val to_location_code: String,  // 更新字段名
    @SerializedName("transfer_quantity") val transfer_quantity: Int,  // 更新字段名
    @SerializedName("batch_number") val batch_number: String?,
    @SerializedName("notes") val notes: String?
)

// 库存调整响应 - 更新为新的API结构
data class InventoryAdjustResponse(
    @SerializedName("location_code") val location_code: String,
    @SerializedName("sku_code") val sku_code: String,
    @SerializedName("previous_quantity") val previous_quantity: Int,
    @SerializedName("adjusted_quantity") val adjusted_quantity: Int,
    @SerializedName("current_quantity") val current_quantity: Int,
    @SerializedName("batch_number") val batch_number: String?,
    @SerializedName("operator_id") val operator_id: String,
    @SerializedName("adjusted_at") val adjusted_at: String,
    @SerializedName("notes") val notes: String?
) 