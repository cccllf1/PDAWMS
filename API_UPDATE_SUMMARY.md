# API 更新总结

## 概述
根据新的后端API文档，已将所有前端代码更新为使用 `snake_case` 命名规范和新的API结构。

## 主要更新内容

### 1. 数据模型更新 (`ApiModels.kt`)

#### 字段名更新
- `total_quantity` → `product_total_quantity` (商品总库存)
- `total_quantity` → `sku_total_quantity` (SKU总库存)  
- `total_quantity` → `color_total_quantity` (颜色总库存)
- `pageSize` → `page_size` (分页大小)
- `from_location` → `from_location_code` (源库位)
- `to_location` → `to_location_code` (目标库位)
- `stock_quantity` → `transfer_quantity` (转移数量)

#### 请求结构简化
- **入库请求**: 简化为 `sku_code`, `location_code`, `stock_quantity`, `operator_id` 等核心字段
- **出库请求**: 简化为 `sku_code`, `location_code`, `stock_quantity`, `operator_id` 等核心字段
- **库存调整请求**: 简化为 `sku_code`, `location_code`, `stock_quantity`, `operator_id` 等核心字段
- **库存转移请求**: 简化为 `sku_code`, `from_location_code`, `to_location_code`, `transfer_quantity`, `operator_id` 等核心字段

### 2. API接口更新 (`ApiService.kt`)

#### 路径参数更新
- `{productId}` → `{product_id}`
- `{skuCode}` → `{sku_code}`
- `{externalCode}` → `{external_code}`

#### 查询参数更新
- `pageSize` → `page_size`

### 3. 业务逻辑更新

#### 入库模块 (`InboundActivity.kt`)
- 更新API调用参数为 `snake_case`
- 更新入库请求构建逻辑
- 更新字段引用（如 `product_total_quantity`）

#### 出库模块 (`OutboundActivity.kt`)
- 更新API调用参数为 `snake_case`
- 更新出库请求构建逻辑
- 更新字段引用（如 `sku_total_quantity`, `product_total_quantity`）
- 更新库存查询逻辑

#### 库存模块 (`InventoryActivity.kt`)
- 更新API调用参数为 `snake_case`
- 更新字段引用（如 `product_total_quantity`, `color_total_quantity`）

#### 扫码模块 (`ScanActivity.kt`)
- 更新SKU外部条码管理API调用
- 更新路径参数为 `snake_case`

### 4. 关键变更点

#### 必需字段
- 所有操作请求现在都需要 `operator_id` 字段
- `sku_code` 成为主要标识字段

#### 可选字段处理
- `location_code` 为 `null` 时表示"无货位"
- 批量号、紧急标记等为可选字段

#### 响应结构
- 保持 `ApiResponse<T>` 通用响应格式
- 错误信息通过 `error_message` 字段返回

## 测试建议

### 1. 接口测试
```bash
# 测试商品查询
curl -X GET "http://192.168.11.252:8610/api/products?page=1&page_size=10" \
  -H "Authorization: Bearer YOUR_TOKEN"

# 测试入库操作
curl -X POST "http://192.168.11.252:8610/api/inbound" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "sku_code": "129092-黄色-M",
    "location_code": "A01-01-01",
    "stock_quantity": 10,
    "operator_id": "user123"
  }'
```

### 2. 功能测试
- 入库功能：扫描商品条码，选择库位，确认入库
- 出库功能：扫描商品条码，选择库位和数量，确认出库
- 库存查询：按商品编码或条码搜索库存信息
- 扫码绑定：绑定SKU和外部条码

## 注意事项

1. **向后兼容性**: 新API结构不兼容旧版本，需要同时更新前后端
2. **字段验证**: 确保所有必需字段都有值，特别是 `operator_id`
3. **错误处理**: 注意处理API返回的错误信息
4. **测试覆盖**: 建议全面测试所有功能模块

## 文件清单

已更新的文件：
- `app/src/main/java/com/yourcompany/wmssystem/pdawms/ApiModels.kt`
- `app/src/main/java/com/yourcompany/wmssystem/pdawms/ApiService.kt`
- `app/src/main/java/com/yourcompany/wmssystem/pdawms/InboundActivity.kt`
- `app/src/main/java/com/yourcompany/wmssystem/pdawms/OutboundActivity.kt`
- `app/src/main/java/com/yourcompany/wmssystem/pdawms/InventoryActivity.kt`
- `app/src/main/java/com/yourcompany/wmssystem/pdawms/ScanActivity.kt`

## 版本信息

- **更新日期**: 2024年12月
- **API版本**: v2.0 (snake_case)
- **兼容性**: 需要后端API同步更新 