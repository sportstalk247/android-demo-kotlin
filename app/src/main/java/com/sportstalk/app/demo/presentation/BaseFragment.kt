package com.sportstalk.app.demo.presentation

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.koin.getViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

open class BaseFragment : Fragment() {

    // Top-level Nav Controller Instance
    open val appNavController: NavController
        get() = findNavController()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        requireActivity().onBackPressedDispatcher
            .addCallback(
                owner = this@BaseFragment,
                enabled = enableBackPressedCallback(),
                onBackPressed = onBackPressedCallback()
            )
    }

    /**
     * Helper functions to handle on back pressed callback
     * - Override this and set to `true` if you want the implementing Fragment to enable onBackPressedCallback implementation
     */
    open fun enableBackPressedCallback(): Boolean = false

    /**
     * Override this and set to `true` if you want the implementing Fragment to have its own onBackPressedCallback implementation
     */
    open fun onBackPressedCallback(): OnBackPressedCallback.() -> Unit = {}

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel Coroutine Flow Subscriptions launched on `lifecycleScope`
        lifecycleScope.coroutineContext.cancelChildren()
    }

    /**
     * Log TAG
     */
    protected val TAG = this::class.java.simpleName

}

/**
 * Helper function to conveniently retrieve ViewModel scoped by navigation graph(ex. nested nav graph)
 * https://github.com/InsertKoinIO/koin/issues/442#issuecomment-623058728
 */
inline fun <reified VM : ViewModel> BaseFragment.sharedGraphViewModel(
    @IdRes navGraphId: Int,
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
) = lazy {
    getSharedGraphViewModel<VM>(navGraphId, qualifier, parameters)
}

inline fun <reified VM : ViewModel> BaseFragment.getSharedGraphViewModel(
    @IdRes navGraphId: Int,
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): VM =
    getKoin().getViewModel(
        findNavController().getViewModelStoreOwner(
            navGraphId
        ),
        VM::class, qualifier, parameters
    )