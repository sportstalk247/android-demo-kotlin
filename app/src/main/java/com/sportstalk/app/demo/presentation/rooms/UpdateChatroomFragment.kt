package com.sportstalk.app.demo.presentation.rooms

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentUpdateChatroomBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.listparticipants.ChatroomListParticipantsFragment
import com.sportstalk.datamodels.chat.ChatRoom
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import reactivecircus.flowbinding.android.widget.checkedChanges
import reactivecircus.flowbinding.android.widget.textChanges
import java.text.SimpleDateFormat
import java.util.*

class UpdateChatroomFragment : BaseFragment() {

    private lateinit var binding: FragmentUpdateChatroomBinding

    private val updateChatroomViewModel: UpdateChatroomViewModel by viewModel {
        parametersOf(
            room,
            user
        )
    }

    /*private val chatroomParticipantsViewModel: ChatroomListParticipantsViewModel by lazy {
        getKoin().getViewModel<ChatroomListParticipantsViewModel>(
            owner = this@UpdateChatroomFragment,
            parameters = {
                parametersOf(
                    room,
                    user,
                    SportsTalk247.UserClient(config),
                    SportsTalk247.ChatClient(config)
                )
            }
        )
    }*/

    private lateinit var user: User
    private lateinit var room: ChatRoom

    private lateinit var menu: Menu

    override fun enableBackPressedCallback(): Boolean = true
    override fun onBackPressedCallback(): OnBackPressedCallback.() -> Unit = {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.discard_changes)
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                dialog.dismiss()
                appNavController.popBackStack()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, which ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        user = requireArguments().getParcelable(INPUT_ARG_USER)!!
        room = requireArguments().getParcelable(INPUT_ARG_ROOM)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentUpdateChatroomBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.let { appActivity ->
            appActivity.setSupportActionBar(binding.toolbar)
            appActivity.supportActionBar?.title = room.name
            appActivity.supportActionBar?.setHomeButtonEnabled(true)
            appActivity.supportActionBar?.setDisplayShowHomeEnabled(true)
        }

        // Attach ChatroomListParticipantsFragment
        childFragmentManager
            .beginTransaction()
            .replace(
                binding.fragmentChatroomParticipants.id,
                ChatroomListParticipantsFragment().apply {
                    this.arguments = bundleOf(
                        ChatroomListParticipantsFragment.INPUT_ARG_ROOM to room,
                        ChatroomListParticipantsFragment.INPUT_ARG_USER to user
                    )
                }
            )
            .commit()

        ///////////////////////////////
        // Bind ViewModel State
        ///////////////////////////////

        /**
         * Emits initial Room info
         */
        updateChatroomViewModel.state.initialRoomName()
            .onEach(::takeInitialRoomName)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.initialRoomDescription()
            .onEach(::takeInitialRoomDescription)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.initialRoomCustomId()
            .onEach(::takeInitialRoomCustomId)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.initialRoomAction()
            .onEach(::takeInitialRoomAction)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.initialRoomEnterExit()
            .onEach(::takeInitialRoomEnterExit)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.initialRoomIsOpen()
            .onEach(::takeInitialRoomIsOpen)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.initialRoomProfanityFilter()
            .onEach(::takeInitialRoomProfanityFilter)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.roomAdded()
            .onEach(::takeRoomAdded)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.roomModified()
            .onEach(::takeRoomModified)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.roomModeration()
            .onEach(::takeRoomModeration)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.roomMaxReports()
            .onEach(::takeRoomMaxReports)
            .launchIn(lifecycleScope)
        updateChatroomViewModel.state.roomAttendeesCount()
            .onEach(::takeRoomAttendeesCount)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Get Chatroom Details operation. Emits [false] when done.
         */
        updateChatroomViewModel.state.progressGetChatroomDetails()
            .onEach(::takeProgressGetChatroomDetails)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Room name value is valid. Otherwise, emits [false].
         */
        updateChatroomViewModel.state.validationRoomName()
            .onEach { takeValidationRoomName(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Room name is valid. Otherwise, emits [false].
         */
        updateChatroomViewModel.state.enableSave()
            .onEach { takeEnableSave(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Create Chatroom operation. Emits [false] when done.
         */
        updateChatroomViewModel.state.progressUpdateChatroom()
            .onEach { takeProgressUpdateChatroom(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Delete All Events in Room operation. Emits [false] when done.
         */
        updateChatroomViewModel.state.progressDeleteAllEventsInRoom()
            .onEach(::takeProgressDeleteAllEventsInRoom)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Delete Room operation. Emits [false] when done.
         */
        updateChatroomViewModel.state.progressDeleteRoom()
            .onEach(::takeProgressDeleteRoom)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start SDK Execute Chat Command Operation(Announcement). Emits [false] when done.
         */
        updateChatroomViewModel.state.progressSendAnnouncement()
            .onEach(::takeProgressSendAnnouncement)
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind View Effect
        ///////////////////////////////
        updateChatroomViewModel.effect
            .onEach { takeViewEffect(it) }
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind UI Input Actions
        ///////////////////////////////

        // Room Name
        binding.tietName.textChanges(emitImmediately = false)
            .debounce(350)
            .map { it.toString() }
            .onEach { updateChatroomViewModel.roomName(it) }
            .launchIn(lifecycleScope)

        // Room Description
        binding.tietDescription.textChanges()
            .debounce(350)
            .map { it.toString() }
            .onEach { updateChatroomViewModel.roomDescription(it) }
            .launchIn(lifecycleScope)

        // Room Custom ID
        binding.tietCustomId.textChanges()
            .debounce(350)
            .map { it.toString() }
            .onEach { updateChatroomViewModel.roomCustomId(it) }
            .launchIn(lifecycleScope)

        // Initially Perform Get Chatroom Details
        updateChatroomViewModel.getChatroomDetails()

    }

    private suspend fun takeInitialRoomName(name: String?) {
        Log.d(TAG, "takeInitialRoomName() -> name = $name")
        binding.tietName.setText(name)
    }

    private suspend fun takeInitialRoomDescription(description: String?) {
        Log.d(TAG, "takeInitialRoomDescription() -> description = $description")
        binding.tietDescription.setText(description)
    }

    private suspend fun takeInitialRoomCustomId(customId: String?) {
        Log.d(TAG, "takeInitialRoomCustomId() -> customId = $customId")
        binding.tietCustomId.setText(customId)
    }

    private suspend fun takeInitialRoomAction(enabled: Boolean?) {
        Log.d(TAG, "takeInitialRoomAction() -> enabled = $enabled")
        binding.switchEnableActions.isChecked = enabled == true

        binding.switchEnableActions.checkedChanges()
            .onEach { checked ->
                updateChatroomViewModel.roomAction(checked)
                binding.switchEnableActions.text = when (checked) {
                    true -> getString(R.string.action_enabled)
                    false -> getString(R.string.action_disabled)
                }
            }
            .launchIn(lifecycleScope)
    }

    private suspend fun takeInitialRoomEnterExit(enabled: Boolean?) {
        Log.d(TAG, "takeInitialRoomEnterExit() -> enabled = $enabled")
        binding.switchEnableEnterExit.isChecked = enabled == true

        binding.switchEnableEnterExit.checkedChanges()
            .onEach { checked ->
                updateChatroomViewModel.roomEnterExit(checked)
                binding.switchEnableEnterExit.text = when (checked) {
                    true -> getString(R.string.enter_exit_enabled)
                    false -> getString(R.string.enter_exit_disabled)
                }
            }
            .launchIn(lifecycleScope)
    }

    private suspend fun takeInitialRoomIsOpen(enabled: Boolean?) {
        Log.d(TAG, "takeInitialRoomIsOpen() -> enabled = $enabled")
        binding.switchRoomOpen.isChecked = enabled == true

        binding.switchRoomOpen.checkedChanges()
            .onEach { checked ->
                updateChatroomViewModel.roomIsOpen(checked)
                binding.switchRoomOpen.text = when (checked) {
                    true -> getString(R.string.room_is_open)
                    false -> getString(R.string.room_is_closed)
                }
            }
            .launchIn(lifecycleScope)
    }

    private suspend fun takeInitialRoomProfanityFilter(enabled: Boolean?) {
        Log.d(TAG, "takeInitialRoomProfanityFilter() -> enabled = $enabled")
        binding.switchProfanityFilter.isChecked = enabled == true

        binding.switchProfanityFilter.checkedChanges()
            .onEach { checked ->
                updateChatroomViewModel.roomProfanityEnabled(checked)
                binding.switchProfanityFilter.text = when (checked) {
                    true -> getString(R.string.profanity_filter_enabled)
                    false -> getString(R.string.profanity_filter_disabled)
                }
            }
            .launchIn(lifecycleScope)
    }

    private suspend fun takeRoomAdded(roomAdded: String?) {
        Log.d(TAG, "takeRoomAdded() -> roomAdded = $roomAdded")
        val added = roomAdded ?: return
        val utcAdded = UTC_DATE_FORMATTER.parse(added) ?: return
        val uiAdded = UI_DATE_FORMATTER.format(utcAdded)

        binding.actvAdded.text = getString(R.string.added_date, uiAdded)
    }

    private suspend fun takeRoomModified(roomModified: String?) {
        Log.d(TAG, "takeRoomModified() -> roomModified = $roomModified")
        val modified = roomModified ?: return
        val utcModified = UTC_DATE_FORMATTER.parse(modified) ?: return
        val uiModified = UI_DATE_FORMATTER.format(utcModified)

        binding.actvModified.text = getString(R.string.modified_date, uiModified)
    }

    private suspend fun takeRoomModeration(moderation: String?) {
        Log.d(TAG, "takeRoomModeration() -> moderation = $moderation")

        binding.actvModeration.text = moderation?.let {
            getString(R.string.moderation_type, it)
        } ?: ""
    }

    private suspend fun takeRoomMaxReports(maxReports: Long?) {
        Log.d(TAG, "takeRoomMaxReports() -> maxReports = $maxReports")
        binding.actvMaxReports.text = maxReports?.let {
            getString(R.string.max_reports_n, it.toString(10))
        } ?: ""
    }

    private suspend fun takeRoomAttendeesCount(inroomCount: Long?) {
        Log.d(TAG, "takeRoomAttendeesCount() -> inroomCount = $inroomCount")
        binding.actvAttendance.text = inroomCount?.let {
            getString(R.string.inroom_n, it.toString(10))
        } ?: ""
    }

    private suspend fun takeProgressGetChatroomDetails(inProgress: Boolean) {
        Log.d(TAG, "takeProgressGetChatroomDetails() -> inProgress = $inProgress")

        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            false -> View.GONE
        }

        binding.containerUpdateRoomDetails.isEnabled = !inProgress

    }

    private fun takeValidationRoomName(isValid: Boolean) {
        Log.d(TAG, "takeValidationRoomName() -> isValid = $isValid")

        when (isValid) {
            true -> {
                binding.tilName.error = ""
                binding.tilName.isErrorEnabled = false
            }
            false -> {
                binding.tilName.error = getString(R.string.invalid_roomname)
                binding.tilName.isErrorEnabled = true
            }
        }
    }

    private fun takeEnableSave(isEnabled: Boolean) {
        Log.d(TAG, "takeEnableSave() -> isEnabled = $isEnabled")
        this.menu.findItem(R.id.action_save).isEnabled = isEnabled
    }

    private fun takeProgressUpdateChatroom(inProgress: Boolean) {
        Log.d(TAG, "takeProgressUpdateChatroom() -> inProgress = $inProgress")
        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            else -> View.GONE
        }
    }

    private suspend fun takeProgressDeleteAllEventsInRoom(inProgress: Boolean) {
        Log.d(TAG, "takeProgressDeleteAllEventsInRoom() -> inProgress = $inProgress")
        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            else -> View.GONE
        }
    }

    private suspend fun takeProgressDeleteRoom(inProgress: Boolean) {
        Log.d(TAG, "takeProgressDeleteRoom() -> inProgress = $inProgress")
        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            else -> View.GONE
        }
    }

    private suspend fun takeProgressSendAnnouncement(inProgress: Boolean) {
        Log.d(TAG, "takeProgressSendAnnouncement() -> inProgress = $inProgress")
        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            else -> View.GONE
        }
    }

    private fun takeViewEffect(effect: UpdateChatroomViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when (effect) {
            is UpdateChatroomViewModel.ViewEffect.ErrorGetChatroomDetails -> {
                // Display ERROR Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                ).show()

                // Pop back
                appNavController.popBackStack()
            }
            is UpdateChatroomViewModel.ViewEffect.SuccessUpdateChatroom -> {
                // Success Prompt
                Toast.makeText(
                    requireContext(),
                    getString(R.string.chatroom_successfully_updated, effect.room.name ?: ""),
                    Toast.LENGTH_LONG
                ).show()

                // Pop back
                appNavController.popBackStack()
            }
            is UpdateChatroomViewModel.ViewEffect.ErrorUpdateChatroom -> {
                // Display ERROR Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                ).show()
            }
            is UpdateChatroomViewModel.ViewEffect.SuccessDeleteAllEventsInRoom -> {
                // Success Prompt
                Toast.makeText(
                    requireContext(),
                    getString(R.string.all_events_in_this_chatroom_successfully_deleted),
                    Toast.LENGTH_LONG
                ).show()
            }
            is UpdateChatroomViewModel.ViewEffect.ErrorDeleteAllEventsInRoom -> {
                // Display ERROR Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                ).show()
            }
            is UpdateChatroomViewModel.ViewEffect.SuccessDeleteRoom -> {
                // Success Prompt
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.chatroom_successfully_deleted,
                        effect.response.room?.name ?: ""
                    ),
                    Toast.LENGTH_LONG
                ).show()

                // Pop back
                appNavController.popBackStack()
            }
            is UpdateChatroomViewModel.ViewEffect.ErrorDeleteRoom -> {
                // Display ERROR Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                ).show()
            }
            is UpdateChatroomViewModel.ViewEffect.SuccessSendAnnouncement -> {
                Toast.makeText(
                    requireContext(),
                    R.string.announcement_successfully_sent,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is UpdateChatroomViewModel.ViewEffect.ErrorSendAnnouncement -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        this.menu = menu
        inflater.inflate(R.menu.update_chatroom, this.menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressed()
                true
            }
            R.id.action_save -> {
                updateChatroomViewModel.save()
                true
            }
            R.id.action_send_announcement -> {
                val textInputLayout = LayoutInflater.from(requireContext())
                    .inflate(
                        R.layout.layout_inapp_settings_input_text,
                        binding.root,
                        false
                    ) as TextInputLayout
                val tietInputText =
                    textInputLayout.findViewById<TextInputEditText>(R.id.tietInputText)

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.send_announcement)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, _ ->
                        // Attempt send announcement
                        updateChatroomViewModel.sendAnnouncement(
                            message = tietInputText.text?.toString() ?: ""
                        )
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()

                true
            }
            R.id.action_delete_all_events -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(android.R.string.yes) { dialog, _ ->
                        // Attempt perform delete all events
                        updateChatroomViewModel.deleteAllChatEvents()
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()

                true
            }
            R.id.action_delete_room -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(android.R.string.yes) { dialog, _ ->
                        // Attempt perform delete chatroom
                        updateChatroomViewModel.deleteChatroom()
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"

        private val UTC_DATE_FORMATTER: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        private val UI_DATE_FORMATTER: SimpleDateFormat =
            SimpleDateFormat("hh:mm aa MMM dd, yyyy", Locale.getDefault())
    }

}