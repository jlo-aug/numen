package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.SymLayoutController

/** Owns explicit show requests; framework callbacks alone never prove that a child was drawn. */
class KeyboardVisibilityController(
    private val context: Context,
    private val candidatesBarController: CandidatesBarController,
    private val symLayoutController: SymLayoutController,
    private val isInputViewActive: () -> Boolean,
    private val isNavModeLatched: () -> Boolean,
    private val currentInputConnection: () -> InputConnection?,
    private val isInputViewShown: () -> Boolean,
    private val renderedSurface: () -> RenderedSurface,
    private val setRequestedInputViewShown: (Boolean) -> Unit,
    private val attachInputView: (View) -> Unit,
    private val attachCandidatesView: (View) -> Unit,
    private val setCandidatesSurfaceActive: (Boolean) -> Unit,
    private val setCandidatesViewShown: (Boolean) -> Unit,
    private val synchronizeCandidatesContainerVisibility: () -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val postToUiDelayed: (Long, () -> Unit) -> Unit,
    private val showInputWindow: (Boolean) -> Unit,
    private val hideInputWindow: () -> Unit,
    private val requestHideInputView: () -> Unit,
    private val requestShowInputView: () -> Unit,
    private val refreshStatusBar: () -> Unit,
    private val setSurfaceCollapsed: (Boolean) -> Unit = {},
    private val trace: (String) -> Unit = {}
) {
    enum class RenderedSurface { HIDDEN, FULL_INPUT_VIEW, CANDIDATES_VIEW }

    private var generation = 0
    private var pending = false
    private var changingSurface = false
    private var dismissed = false
    private var candidatesStarted = false
    private var windowShown = false
    private var waitingForBackendHide = false
    /** The window is shown but its bar is collapsed to zero height (silent hardware typing). */
    private var collapsed = false

    fun usesCandidatesView(): Boolean =
        SettingsManager.getExperimentalCandidatesViewEnabled(context) &&
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) !=
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL

    private fun expectedSurface() = if (usesCandidatesView()) {
        RenderedSurface.CANDIDATES_VIEW
    } else {
        RenderedSurface.FULL_INPUT_VIEW
    }

    fun onCreateInputView(): View =
        candidatesBarController.getInputView(symLayoutController.emojiMapTextForLayout()).also {
            detachFromParent(it)
            refreshStatusBar()
        }

    fun onCreateCandidatesView(): View {
        setCandidatesSurfaceActive(usesCandidatesView())
        return candidatesBarController.getCandidatesView(symLayoutController.emojiMapTextForLayout()).also {
            detachFromParent(it)
            refreshStatusBar()
        }
    }

    fun onEvaluateInputViewShown(shouldShowInputView: Boolean): Boolean {
        SoftwareKeyboardAutoDetector.updateSystemInputViewDecision(shouldShowInputView)
        // The stable backend deliberately uses an input view even for the compact hardware row.
        // Keep evaluation pure: a show from here re-enters Android's unfinished show operation.
        return !usesCandidatesView()
    }

    private fun canShow() = isInputViewActive() && currentInputConnection() != null && !isNavModeLatched()

    fun ensureImeSurfaceVisible() = requestSurface(wantCollapsed = false)

    /**
     * Silent mode keeps the IME window open but empty, so apps still draw their cursor and the
     * first Back closes the keyboard instead of leaving the field.
     */
    private fun requestSurface(wantCollapsed: Boolean) {
        if (!canShow() || waitingForBackendHide) return
        // The experimental candidates backend has no collapsed form; silent mode keeps it hidden.
        if (wantCollapsed && usesCandidatesView()) return
        dismissed = false
        if (!wantCollapsed && collapsed) {
            collapsed = false
            setSurfaceCollapsed(false)
            refreshStatusBar()
        }
        if (pending || isExpectedSurfaceRequestedOrShown()) return
        if (wantCollapsed != collapsed) {
            collapsed = wantCollapsed
            setSurfaceCollapsed(wantCollapsed)
        }
        pending = true
        val ticket = ++generation
        // onStartInput/onShowInputRequested may be inside a framework hide/show. Wait for it to end.
        postToUi { present(ticket, 0) }
    }

    private fun present(ticket: Int, attempt: Int) {
        if (ticket != generation || !pending) return
        if (!canShow() || dismissed) {
            cancelPendingSurfaceTransition()
            return
        }
        if (isExpectedSurfaceRequestedOrShown()) {
            pending = false
            trace("shown target=${expectedSurface()} attempt=$attempt")
            return
        }
        if (attempt >= MAX_ATTEMPTS) {
            // A failed show must not leave an invisible window intercepting the editor.
            pending = false
            changingSurface = true
            setCandidatesSurfaceActive(false)
            setCandidatesViewShown(false)
            hideInputWindow()
            requestHideInputView()
            changingSurface = false
            trace("show_failed target=${expectedSurface()}; window closed")
            return
        }
        trace("show target=${expectedSurface()} attempt=$attempt rendered=${renderedSurface()}")
        changingSurface = true
        try {
            val candidates = usesCandidatesView()
            setRequestedInputViewShown(!candidates)
            setCandidatesSurfaceActive(candidates)
            if (candidates) {
                // Rebind the actual child, even when Android still reports candidates-started.
                // setCandidatesViewShown(true) alone is an idempotent no-op in that ghost state.
                attachCandidatesView(onCreateCandidatesView())
                setCandidatesViewShown(true)
                synchronizeCandidatesContainerVisibility()
                showInputWindow(false)
                synchronizeCandidatesContainerVisibility()
                // Direct child/window repair does not restore IMMS's show request after Back.
                // Issue one server request for this explicit recovery, never one per retry/key.
                if (attempt == 0) requestShowInputView()
            } else {
                setCandidatesViewShown(false)
                attachInputView(onCreateInputView())
                if (attempt == 0) requestShowInputView()
                else showInputWindow(true)
            }
            refreshStatusBar()
        } catch (error: RuntimeException) {
            // A disappearing editor/window can reject a request. Retry within the same bound.
            trace("show_rejected target=${expectedSurface()} error=${error.javaClass.simpleName}")
        } finally {
            changingSurface = false
        }
        postToUiDelayed(RETRY_DELAY_MS) { present(ticket, attempt + 1) }
    }

    /**
     * Silent hardware typing: with auto-show off and a physical keyboard, the bar stays hidden
     * until a SYM tap or an explicit action asks for it.
     */
    private fun isSilentHardwareMode(autoShowKeyboardEnabled: Boolean): Boolean =
        // Numen only; Pastiera always shows its bar
            !autoShowKeyboardEnabled &&
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) !=
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL

    /**
     * Numen's auto-show setting only applies to the hardware keyboard; the on-screen keyboard
     * always shows.
     */
    fun shouldShowSurfaceOnInputStart(autoShowKeyboardEnabled: Boolean): Boolean {
        val virtual = SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) ==
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        return autoShowKeyboardEnabled || virtual
    }

    /** A SYM tap on a hidden or collapsed bar only reveals it; the SYM page opens on the next tap. */
    fun shouldSymRevealSurfaceOnly(autoShowKeyboardEnabled: Boolean): Boolean =
        isSilentHardwareMode(autoShowKeyboardEnabled) && (collapsed || !isExpectedSurfaceRequestedOrShown())

    /**
     * Silent mode answers framework show requests with the collapsed window. Without an editable
     * target, or on the experimental backend (which cannot collapse), it declines unless the show
     * is `pending` or the surface is already up.
     */
    fun shouldAcceptShowRequest(autoShowKeyboardEnabled: Boolean, configChange: Boolean): Boolean =
        !isSilentHardwareMode(autoShowKeyboardEnabled) || configChange || pending ||
            isExpectedSurfaceRequestedOrShown() || (canShow() && !usesCandidatesView())

    fun isSurfaceCollapsed() = collapsed


    fun onImeWindowVisibilityChanged(shown: Boolean) {
        windowShown = shown
        trace("window shown=$shown changing=$changingSurface")
        if (!shown && waitingForBackendHide) {
            waitingForBackendHide = false
            // Let Android finish clearing mShowInputRequested before starting the new backend.
            val ticket = generation
            // Stay collapsed only if the new backend is still silent hardware typing.
            postToUi {
                if (ticket == generation) {
                    requestSurface(
                        wantCollapsed = collapsed &&
                            isSilentHardwareMode(SettingsManager.getAutoShowKeyboard(context))
                    )
                }
            }
            return
        }
        if (!shown && !changingSurface) {
            dismissed = true
            cancelPendingSurfaceTransition()
        }
    }

    fun onCandidatesViewStarted() {
        candidatesStarted = true
        if (usesCandidatesView()) setCandidatesSurfaceActive(true)
        trace("candidates_started rendered=${renderedSurface()}")
    }

    fun onCandidatesViewFinished(finishingInput: Boolean) {
        val wasStarted = candidatesStarted
        candidatesStarted = false
        trace("candidates_finished finishing=$finishingInput changing=$changingSurface")
        if (changingSurface || waitingForBackendHide || finishingInput || !wasStarted || !usesCandidatesView()) return
        dismissed = true
        cancelPendingSurfaceTransition()
        val ticket = generation
        postToUi {
            if (ticket != generation || !dismissed || candidatesStarted) return@postToUi
            changingSurface = true
            setCandidatesSurfaceActive(false)
            setCandidatesViewShown(false)
            hideInputWindow()
            requestHideInputView()
            changingSurface = false
        }
    }

    fun onInputUnbound() {
        cancelPendingSurfaceTransition()
        windowShown = false
        candidatesStarted = false
        dismissed = false
        trace("input_unbound")
    }

    fun onInputStarted(restarting: Boolean) {
        cancelPendingSurfaceTransition()
        if (!restarting) dismissed = false
    }

    /** Framework show request or typing key; collapsed in silent mode. */
    fun onShowRequested(autoShowKeyboardEnabled: Boolean) =
        requestSurface(wantCollapsed = isSilentHardwareMode(autoShowKeyboardEnabled))

    fun onKeyboardSurfaceChanged(ensureInputViewShown: Boolean) {
        cancelPendingSurfaceTransition()
        if (windowShown) {
            // hideWindow() only hides the local window. The framework's hideSoftInput path
            // also resets mShowInputRequested and finishes the preceding input/candidates view.
            waitingForBackendHide = true
            val ticket = generation
            try {
                requestHideInputView()
                postToUiDelayed(BACKEND_HIDE_TIMEOUT_MS) {
                    if (ticket == generation && waitingForBackendHide) abortBackendHide()
                }
            } catch (error: RuntimeException) {
                trace("backend_hide_rejected error=${error.javaClass.simpleName}")
                abortBackendHide()
            }
        } else {
            setCandidatesSurfaceActive(false)
            setCandidatesViewShown(false)
            if (ensureInputViewShown) ensureImeSurfaceVisible()
        }
    }

    private fun abortBackendHide() {
        // Do not reopen against an unacknowledged hide, but allow the next explicit tap/key.
        cancelPendingSurfaceTransition()
        dismissed = true
        trace("backend_hide_aborted")
    }

    fun cancelPendingSurfaceTransition() {
        generation++
        pending = false
        waitingForBackendHide = false
    }

    fun isCandidatesOnlySurface() = usesCandidatesView()
    fun isExpectedSurfaceRequestedOrShown(): Boolean = windowShown && collapsed ||
        windowShown && renderedSurface() == expectedSurface() &&
        (usesCandidatesView() || isInputViewShown())
    fun shouldRecoverSurfaceOnHardwareKey() = !isExpectedSurfaceRequestedOrShown()
    internal fun isCandidatesSurfaceExplicitlyDismissedForTests() = dismissed

    fun togglePastierinaMode() {
        val next = when (SettingsManager.getStatusBarPresentationMode(context)) {
            SettingsManager.StatusBarPresentationMode.PASTIERINA -> SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR
            SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR -> SettingsManager.StatusBarPresentationMode.PASTIERINA
        }
        SettingsManager.setStatusBarPresentationMode(context, next)
        syncStatusBarPresentationModeFromSettings()
    }

    fun syncStatusBarPresentationModeFromSettings() {
        val minimal = SettingsManager.getStatusBarPresentationMode(context) == SettingsManager.StatusBarPresentationMode.PASTIERINA
        candidatesBarController.setPastierinaModeActive(minimal)
        SettingsManager.setPastierinaModeActive(context, minimal)
        refreshStatusBar()
    }

    private fun detachFromParent(view: View) { (view.parent as? ViewGroup)?.removeView(view) }

    private companion object {
        const val BACKEND_HIDE_TIMEOUT_MS = 1000L
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 150L
    }
}
