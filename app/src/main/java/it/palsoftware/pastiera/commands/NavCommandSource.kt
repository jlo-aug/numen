package it.palsoftware.pastiera.commands

import android.content.Context
import it.palsoftware.pastiera.R

class NavCommandSource : CommandSource {
    override val id = CommandSourceId.NavActions

    override fun getCommands(context: Context): List<CommandTarget> {
        return keycodeCommands(context) + actionCommands(context)
    }

    private fun keycodeCommands(context: Context): List<CommandTarget> {
        return listOf(
            "DPAD_UP" to R.string.nav_mode_keycode_up,
            "DPAD_DOWN" to R.string.nav_mode_keycode_down,
            "DPAD_LEFT" to R.string.nav_mode_keycode_left,
            "DPAD_RIGHT" to R.string.nav_mode_keycode_right,
            "TAB" to R.string.nav_mode_keycode_tab,
            "MOVE_HOME" to R.string.nav_mode_keycode_home,
            "MOVE_END" to R.string.nav_mode_keycode_end,
            "PAGE_UP" to R.string.nav_mode_keycode_page_up,
            "PAGE_DOWN" to R.string.nav_mode_keycode_page_down,
            "ESCAPE" to R.string.nav_mode_keycode_escape,
            "DPAD_CENTER" to R.string.nav_mode_keycode_center,
            "FORWARD_DEL" to R.string.nav_mode_keycode_forward_delete
        ).map { (value, labelRes) ->
            navTarget("nav.keycode.$value", context.getString(labelRes), "Navigation", "keycode", value)
        }
    }

    private fun actionCommands(context: Context): List<CommandTarget> {
        return listOf(
            "copy" to R.string.nav_mode_action_copy,
            "paste" to R.string.nav_mode_action_paste,
            "cut" to R.string.nav_mode_action_cut,
            "undo" to R.string.nav_mode_action_undo,
            "select_all" to R.string.nav_mode_action_select_all,
            "expand_selection_left" to R.string.nav_mode_action_expand_selection_left,
            "expand_selection_right" to R.string.nav_mode_action_expand_selection_right,
            "move_word_left" to R.string.nav_mode_action_move_word_left,
            "move_word_right" to R.string.nav_mode_action_move_word_right,
            "expand_selection_word_left" to R.string.nav_mode_action_expand_selection_word_left,
            "expand_selection_word_right" to R.string.nav_mode_action_expand_selection_word_right,
            "page_start" to R.string.nav_mode_action_page_start,
            "page_end" to R.string.nav_mode_action_page_end,
            "toggle_minimal_ui" to R.string.nav_mode_action_toggle_pastierina,
            "media_play_pause" to R.string.nav_mode_action_media_play_pause,
            "media_previous" to R.string.nav_mode_action_media_previous,
            "media_next" to R.string.nav_mode_action_media_next
        ).map { (value, labelRes) ->
            navTarget("nav.action.$value", context.getString(labelRes), "Action", "action", value)
        }
    }

    private fun navTarget(
        id: String,
        label: String,
        subtitle: String,
        mappingType: String,
        value: String
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this.id,
            kind = CommandKind.NavAction,
            label = label,
            subtitle = subtitle,
            icon = CommandIcon.Navigation,
            launch = CommandLaunchSpec.NavAction(mappingType, value),
            capabilities = setOf(CommandCapability.RequiresImeContext),
            defaultSurfaces = setOf(CommandSurface.NavMode),
            searchTokens = listOf(label, subtitle, value)
        )
    }
}
