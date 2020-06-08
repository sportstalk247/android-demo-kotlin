package com.sportstalk.app.demo.presentation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.sportstalk.app.demo.R
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
    getSharedGraphViewModel<VM>(navGraphId, qualifier, parameters)
}

inline fun <reified VM : ViewModel> Fragment.getSharedGraphViewModel(
    @IdRes navGraphId: Int,
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): VM =
    getKoin().getViewModel(
        findNavController()/*Navigation.findNavController(requireActivity(), R.id.navHostFragmentApp)*/.getViewModelStoreOwner(navGraphId),
        VM::class, qualifier, parameters
    )