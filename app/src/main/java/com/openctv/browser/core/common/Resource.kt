package com.openctv.browser.core.common

/** État d'une donnée exposée par un repository ou un ViewModel. */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String?) : Resource<Nothing>
}
