package app.settlezero.core.common

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val throwable: Throwable, val message: String? = throwable.message) : Result<Nothing>
    data object Loading : Result<Nothing>
}

