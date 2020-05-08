package com.sportstalk.app.demo.presentation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.koin.getViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

/**
 * Helper function to conveniently retrieve ViewModel scoped by navigation graph(ex. nested nav graph)
 * https://github.com/InsertKoinIO/koin/issues/442#issuecomment-623058728
 */
inline fun <reified VM : ViewModel> Fragment.sharedGraphViewModel(
    @IdRes navGraphId: Int,
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
) = lazy {
    val owner = findNavController().getViewModelStoreOwner(navGraphId)
    getKoin().getViewModel(owner, VM::class, qualifier, parameters)
}