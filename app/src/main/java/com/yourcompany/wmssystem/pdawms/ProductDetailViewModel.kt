package com.yourcompany.wmssystem.pdawms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Holds all UI state for the product-details dialog.
 */
data class DialogState(
    val colors: List<ColorInfo> = emptyList(),
    val expandedSkuCode: String? = null,
    val expandedLocationCode: String? = null,
    val errorMessage: String? = null
)

class ProductDetailViewModel : ViewModel() {

    private val _state = MutableLiveData(DialogState())
    val state: LiveData<DialogState> = _state

    /**
     * Provide the color list to be displayed in the dialog.
     */
    fun loadColors(list: List<ColorInfo>) {
        _state.value = _state.value?.copy(colors = list)
    }

    /** Toggle the expansion of a SKU row. */
    fun onSkuClicked(skuCode: String) {
        val current = _state.value ?: return
        val newSkuCode = if (current.expandedSkuCode == skuCode) null else skuCode
        _state.value = current.copy(expandedSkuCode = newSkuCode, expandedLocationCode = null)
    }

    /** Toggle the expansion of a location row. */
    fun onLocationClicked(locationCode: String) {
        val current = _state.value ?: return
        val newLoc = if (current.expandedLocationCode == locationCode) null else locationCode
        _state.value = current.copy(expandedLocationCode = newLoc)
    }

    fun setError(msg: String) {
        _state.value = _state.value?.copy(errorMessage = msg)
    }

    fun clearError() {
        _state.value = _state.value?.copy(errorMessage = null)
    }
} 