package io.github.yasarmahomedabbas.immichtv.ui.screensaver

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.yasarmahomedabbas.immichtv.ImmichTvApp
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowViewModel
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

/**
 * The TV's screensaver. A dream isn't an activity, so nothing gives Compose the owners it looks for
 * on the view tree: the service is all three, and dreaming is what counts as being started, which
 * is what runs and stops the show's timer. Not interactive, so any key wakes the TV.
 */
class ScreensaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        window.decorView.setViewTreeViewModelStoreOwner(this)
        val container = (application as ImmichTvApp).container
        val viewModel = ViewModelProvider(this, SlideshowViewModel.forScreensaver(container))[SlideshowViewModel::class.java]
        setContentView(ComposeView(this).apply { setContent { ImmichTvTheme { ScreensaverScreen(viewModel) } } })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDetachedFromWindow()
    }
}
