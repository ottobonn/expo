package abi45_0_0.host.exp.exponent.modules.api.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.Transformation
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import abi45_0_0.com.facebook.react.uimanager.PixelUtil
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior

class ScreenStackFragment : ScreenFragment {
  private var mAppBarLayout: AppBarLayout? = null
  private var mToolbar: Toolbar? = null
  private var mShadowHidden = false
  private var mIsTranslucent = false

  var searchView: CustomSearchView? = null
  var onSearchViewCreate: ((searchView: CustomSearchView) -> Unit)? = null

  @SuppressLint("ValidFragment")
  constructor(screenView: Screen) : super(screenView)

  constructor() {
    throw IllegalStateException(
      "ScreenStack fragments should never be restored. Follow instructions from https://github.com/software-mansion/react-native-screens/issues/17#issuecomment-424704067 to properly configure your main activity."
    )
  }

  fun removeToolbar() {
    mAppBarLayout?.let {
      mToolbar?.let { toolbar ->
        if (toolbar.parent === it) {
          it.removeView(toolbar)
        }
      }
    }
    mToolbar = null
  }

  fun setToolbar(toolbar: Toolbar) {
    mAppBarLayout?.addView(toolbar)
    val params = AppBarLayout.LayoutParams(
      AppBarLayout.LayoutParams.MATCH_PARENT, AppBarLayout.LayoutParams.WRAP_CONTENT
    )
    params.scrollFlags = 0
    toolbar.layoutParams = params
    mToolbar = toolbar
  }

  fun setToolbarShadowHidden(hidden: Boolean) {
    if (mShadowHidden != hidden) {
      mAppBarLayout?.targetElevation = if (hidden) 0f else PixelUtil.toPixelFromDIP(4f)
      mShadowHidden = hidden
    }
  }

  fun setToolbarTranslucent(translucent: Boolean) {
    if (mIsTranslucent != translucent) {
      val params = screen.layoutParams
      (params as CoordinatorLayout.LayoutParams).behavior =
        if (translucent) null else ScrollingViewBehavior()
      mIsTranslucent = translucent
    }
  }

  override fun onContainerUpdate() {
    val headerConfig = screen.headerConfig
    headerConfig?.onUpdate()
  }

  override fun onViewAnimationEnd() {
    super.onViewAnimationEnd()
    notifyViewAppearTransitionEnd()
  }

  private fun notifyViewAppearTransitionEnd() {
    val screenStack = view?.parent
    if (screenStack is ScreenStack) {
      screenStack.onViewAppearTransitionEnd()
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view: ScreensCoordinatorLayout? =
      context?.let { ScreensCoordinatorLayout(it, this) }
    val params = CoordinatorLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
    )
    params.behavior = if (mIsTranslucent) null else ScrollingViewBehavior()
    screen.layoutParams = params
    view?.addView(recycleView(screen))

    mAppBarLayout = context?.let { AppBarLayout(it) }
    // By default AppBarLayout will have a background color set but since we cover the whole layout
    // with toolbar (that can be semi-transparent) the bar layout background color does not pay a
    // role. On top of that it breaks screens animations when alfa offscreen compositing is off
    // (which is the default)
    mAppBarLayout?.setBackgroundColor(Color.TRANSPARENT)
    mAppBarLayout?.layoutParams = AppBarLayout.LayoutParams(
      AppBarLayout.LayoutParams.MATCH_PARENT, AppBarLayout.LayoutParams.WRAP_CONTENT
    )
    view?.addView(mAppBarLayout)
    if (mShadowHidden) {
      mAppBarLayout?.targetElevation = 0f
    }
    mToolbar?.let { mAppBarLayout?.addView(recycleView(it)) }
    setHasOptionsMenu(true)
    return view
  }

  override fun onPrepareOptionsMenu(menu: Menu) {
    updateToolbarMenu(menu)
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    updateToolbarMenu(menu)
    return super.onCreateOptionsMenu(menu, inflater)
  }

  private fun shouldShowSearchBar(): Boolean {
    val config = screen.headerConfig
    val numberOfSubViews = config?.configSubviewsCount ?: 0
    if (config != null && numberOfSubViews > 0) {
      for (i in 0 until numberOfSubViews) {
        val subView = config.getConfigSubview(i)
        if (subView.type == ScreenStackHeaderSubview.Type.SEARCH_BAR) {
          return true
        }
      }
    }
    return false
  }

  private fun updateToolbarMenu(menu: Menu) {
    menu.clear()
    if (shouldShowSearchBar()) {
      val currentContext = context
      if (searchView == null && currentContext != null) {
        val newSearchView = CustomSearchView(currentContext, this)
        searchView = newSearchView
        onSearchViewCreate?.invoke(newSearchView)
      }
      val item = menu.add("")
      item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      item.actionView = searchView
    }
  }

  fun canNavigateBack(): Boolean {
    val container: ScreenContainer<*>? = screen.container
    check(container is ScreenStack) { "ScreenStackFragment added into a non-stack container" }
    return if (container.rootScreen == screen) {
      // this screen is the root of the container, if it is nested we can check parent container
      // if it is also a root or not
      val parentFragment = parentFragment
      if (parentFragment is ScreenStackFragment) {
        parentFragment.canNavigateBack()
      } else {
        false
      }
    } else {
      true
    }
  }

  fun dismiss() {
    val container: ScreenContainer<*>? = screen.container
    check(container is ScreenStack) { "ScreenStackFragment added into a non-stack container" }
    container.dismiss(this)
  }

  private class ScreensCoordinatorLayout(
    context: Context,
    private val mFragment: ScreenFragment
  ) : CoordinatorLayout(context) {
    private val mAnimationListener: Animation.AnimationListener =
      object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
          mFragment.onViewAnimationStart()
        }

        override fun onAnimationEnd(animation: Animation) {
          mFragment.onViewAnimationEnd()
        }

        override fun onAnimationRepeat(animation: Animation) {}
      }

    override fun startAnimation(animation: Animation) {
      // For some reason View##onAnimationEnd doesn't get called for
      // exit transitions so we explicitly attach animation listener.
      // We also have some animations that are an AnimationSet, so we don't wrap them
      // in another set since it causes some visual glitches when going forward.
      // We also set the listener only when going forward, since when going back,
      // there is already a listener for dismiss action added, which would be overridden
      // and also this is not necessary when going back since the lifecycle methods
      // are correctly dispatched then.
      // We also add fakeAnimation to the set of animations, which sends the progress of animation
      val fakeAnimation = ScreensAnimation(mFragment)
      fakeAnimation.duration = animation.duration
      if (animation is AnimationSet && !mFragment.isRemoving) {
        animation.addAnimation(fakeAnimation)
        animation.setAnimationListener(mAnimationListener)
        super.startAnimation(animation)
      } else {
        val set = AnimationSet(true)
        set.addAnimation(animation)
        set.addAnimation(fakeAnimation)
        set.setAnimationListener(mAnimationListener)
        super.startAnimation(set)
      }
    }

    /**
     * This method implements a workaround for RN's autoFocus functionality. Because of the way
     * autoFocus is implemented it dismisses soft keyboard in fragment transition
     * due to change of visibility of the view at the start of the transition. Here we override the
     * call to `clearFocus` when the visibility of view is `INVISIBLE` since `clearFocus` triggers the
     * hiding of the keyboard in `ReactEditText.java`.
     */
    override fun clearFocus() {
      if (visibility != INVISIBLE) {
        super.clearFocus()
      }
    }
  }

  private class ScreensAnimation(private val mFragment: ScreenFragment) : Animation() {
    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
      super.applyTransformation(interpolatedTime, t)
      // interpolated time should be the progress of the current transition
      mFragment.dispatchTransitionProgress(interpolatedTime, !mFragment.isResumed)
    }
  }
}
