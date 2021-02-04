/*
 * Copyright © 2020 Tinkoff Bank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package ru.tinkoff.acquiring.sdk.ui.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import ru.tinkoff.acquiring.sdk.R
import ru.tinkoff.acquiring.sdk.TinkoffAcquiring
import ru.tinkoff.acquiring.sdk.adapters.CardsViewPagerAdapter
import ru.tinkoff.acquiring.sdk.cardscanners.CameraCardScanner.Companion.REQUEST_CAMERA_CARD_SCAN
import ru.tinkoff.acquiring.sdk.cardscanners.CardScanner
import ru.tinkoff.acquiring.sdk.cardscanners.CardScanner.Companion.REQUEST_CARD_NFC
import ru.tinkoff.acquiring.sdk.localization.AsdkLocalization
import ru.tinkoff.acquiring.sdk.localization.AsdkSource
import ru.tinkoff.acquiring.sdk.localization.Language
import ru.tinkoff.acquiring.sdk.models.AsdkState
import ru.tinkoff.acquiring.sdk.models.Card
import ru.tinkoff.acquiring.sdk.models.ErrorButtonClickedEvent
import ru.tinkoff.acquiring.sdk.models.ErrorScreenState
import ru.tinkoff.acquiring.sdk.models.LoadingState
import ru.tinkoff.acquiring.sdk.models.RejectedState
import ru.tinkoff.acquiring.sdk.models.ScreenState
import ru.tinkoff.acquiring.sdk.models.SelectCardAndPayState
import ru.tinkoff.acquiring.sdk.models.options.screen.PaymentOptions
import ru.tinkoff.acquiring.sdk.models.options.screen.SavedCardsOptions
import ru.tinkoff.acquiring.sdk.models.paysources.CardData
import ru.tinkoff.acquiring.sdk.ui.activities.BaseAcquiringActivity
import ru.tinkoff.acquiring.sdk.ui.activities.SavedCardsActivity
import ru.tinkoff.acquiring.sdk.ui.customview.editcard.EditCardScanButtonClickListener
import ru.tinkoff.acquiring.sdk.ui.customview.scrollingindicator.ScrollingPagerIndicator
import ru.tinkoff.acquiring.sdk.viewmodel.PaymentViewModel

/**
 * @author Mariya Chernyadieva
 */
internal class PaymentFragment : BaseAcquiringFragment(), EditCardScanButtonClickListener {

    private lateinit var cardsPagerAdapter: CardsViewPagerAdapter
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var paymentOptions: PaymentOptions
    private lateinit var cardScanner: CardScanner
    private lateinit var asdkState: AsdkState

    private lateinit var emailHintTextView: TextView
    private lateinit var orderDescription: TextView
    private lateinit var pagerIndicator: ScrollingPagerIndicator
    private lateinit var amountTextView: TextView
    private lateinit var emailEditText: EditText
    private lateinit var orderTitle: TextView
    private lateinit var orTextView: TextView
    private lateinit var fpsButton: View
    private lateinit var payButton: Button
    private lateinit var viewPager: ViewPager

    private var customerKey: String? = null
    private var selectedCardId: String? = null
    private var rejectedDialog: AlertDialog? = null
    private var rejectedDialogDismissed = false
    private var viewPagerPosition = FIRST_POSITION

    companion object {
        private const val CUSTOMER_KEY = "customer_key"
        private const val REJECTED_STATE = "rejected_state"
        private const val REJECTED_DIALOG_DISMISSED = "rejected_dialog_dismissed"

        private const val STATE_VIEW_PAGER_POSITION = "state_view_pager_position"
        private const val STATE_SELECTED_CARD_ID = "state_selected_card_id"

        private const val CARD_LIST_REQUEST_CODE = 209

        private const val FIRST_POSITION = 0

        private const val EMAIL_HINT_ANIMATION_DURATION = 200L

        fun newInstance(customerKey: String?, state: RejectedState? = null): Fragment {
            val args = Bundle()
            args.putString(CUSTOMER_KEY, customerKey)
            args.putSerializable(REJECTED_STATE, state)

            val fragment = PaymentFragment()
            fragment.arguments = args

            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        cardScanner = CardScanner(context)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.acq_fragment_payment, container, false)

        val amountLabel = view.findViewById<TextView>(R.id.acq_payment_tv_amount_label)
        amountTextView = view.findViewById(R.id.acq_payment_tv_amount)
        amountLabel.text = AsdkLocalization.resources.payTitle

        emailHintTextView = view.findViewById(R.id.acq_payment_email_tv_hint)
        orderDescription = view.findViewById(R.id.acq_payment_tv_order_description)
        orderTitle = view.findViewById(R.id.acq_payment_tv_order_title)
        orTextView = view.findViewById(R.id.acq_payment_tv_or)
        viewPager = view.findViewById(R.id.acq_payment_viewpager)

        emailEditText = view.findViewById(R.id.acq_payment_et_email)
        if (emailEditText.visibility == View.VISIBLE) {
            emailEditText.addTextChangedListener(createTextChangeListener())
        }

        pagerIndicator = view.findViewById(R.id.acq_payment_page_indicator)
        pagerIndicator.run {
            setOnPlusClickListener(object : ScrollingPagerIndicator.OnPlusIndicatorClickListener {
                override fun onClick() {
                    cardsPagerAdapter.enterCardPosition?.let { position ->
                        viewPager.currentItem = position
                    }
                }
            })
            setOnListClickListener(object : ScrollingPagerIndicator.OnListIndicatorClickListener {
                override fun onClick() {
                    hideSystemKeyboard()
                    emailEditText.clearFocus()
                    val options = getSavedCardOptions()
                    val intent = BaseAcquiringActivity.createIntent(requireActivity(),
                            options,
                            SavedCardsActivity::class.java)
                    startActivityForResult(intent, CARD_LIST_REQUEST_CODE)
                }
            })
        }

        payButton = view.findViewById(R.id.acq_payment_btn_pay)
        payButton.setOnClickListener {
            hideSystemKeyboard()
            processCardPayment()
        }

        fpsButton = view.findViewById(R.id.acq_payment_btn_fps_pay)

        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        requireActivity().intent.extras?.let { extras ->
            setupPaymentOptions(extras)
        }

        requireArguments().let {
            customerKey = it.getString(CUSTOMER_KEY)
            asdkState = it.getSerializable(REJECTED_STATE) as AsdkState? ?: paymentOptions.asdkState
        }

        selectedCardId = paymentOptions.features.selectedCardId

        savedInstanceState?.let {
            rejectedDialogDismissed = it.getBoolean(REJECTED_DIALOG_DISMISSED)
            viewPagerPosition = it.getInt(STATE_VIEW_PAGER_POSITION)
            selectedCardId = it.getString(STATE_SELECTED_CARD_ID)
        }

        paymentOptions.order.run {
            amountTextView.text = modifySpan(amount.toHumanReadableString())
            orderTitle.visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
            orderDescription.visibility = if (description.isNullOrBlank()) View.GONE else View.VISIBLE
            orderTitle.text = title
            orderDescription.text = description
        }

        orderDescription.movementMethod = ScrollingMovementMethod()
        orderDescription.setOnTouchListener { _, _ ->
            val canScroll = orderDescription.canScrollVertically(1) || orderDescription.canScrollVertically(-1)
            orderDescription.parent.requestDisallowInterceptTouchEvent(canScroll)
            false
        }

        setupCardsPager()

        paymentViewModel = ViewModelProvider(requireActivity()).get(PaymentViewModel::class.java)
        val isErrorShowing = paymentViewModel.screenStateLiveData.value is ErrorScreenState
        observeLiveData()

        emailHintTextView.visibility = when {
            emailEditText.visibility != View.VISIBLE -> View.GONE
            savedInstanceState == null && !paymentOptions.customer.email.isNullOrEmpty() -> {
                emailEditText.setText(paymentOptions.customer.email)
                View.VISIBLE
            }
            else -> View.INVISIBLE
        }

        emailHintTextView.text = localization.payEmail
        emailEditText.hint = localization.payEmail
        orTextView.text = localization.payOrText
        fpsButton.findViewById<TextView>(R.id.acq_payment_fps_text).text = localization.payPayWithFpsButton
        (requireActivity() as AppCompatActivity).supportActionBar?.title = localization.payScreenTitle

        if (paymentOptions.features.fpsEnabled) {
            setupFpsButton()
            payButton.text = localization.payPayViaButton
        } else {
            orTextView.visibility = View.GONE
            payButton.text = localization.payPayButton
        }

        if (paymentViewModel.cardsResultLiveData.value == null &&
                paymentViewModel.loadStateLiveData.value != LoadingState && !isErrorShowing) {
            loadCards()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.run {
            putInt(STATE_VIEW_PAGER_POSITION, viewPager.currentItem)
            putBoolean(REJECTED_DIALOG_DISMISSED, rejectedDialogDismissed)
            putString(STATE_SELECTED_CARD_ID, selectedCardId)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CAMERA_CARD_SCAN, REQUEST_CARD_NFC -> {
                val scannedCardData = cardScanner.getScanResult(requestCode, resultCode, data)
                if (scannedCardData != null) {
                    cardsPagerAdapter.enterCardData = CardData(scannedCardData.cardNumber, scannedCardData.expireDate, "")
                } else if (resultCode != Activity.RESULT_CANCELED) {
                    Toast.makeText(this.activity, localization.payNfcFail, Toast.LENGTH_SHORT).show()
                }
            }
            CARD_LIST_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val newCardId = data.getStringExtra(TinkoffAcquiring.EXTRA_CARD_ID)
                    val cardListChanged = data.getBooleanExtra(TinkoffAcquiring.EXTRA_CARD_LIST_CHANGED, false)
                    if (cardListChanged || selectedCardId != newCardId) {
                        selectedCardId = newCardId
                        viewPagerPosition = FIRST_POSITION
                        loadCards()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (rejectedDialog != null && rejectedDialog!!.isShowing) {
            rejectedDialog?.dismiss()
        }
    }

    override fun onScanButtonClick() {
        cardScanner.scanCard()
    }

    private fun setupPaymentOptions(extras: Bundle) {
        paymentOptions = extras.getParcelable(BaseAcquiringActivity.EXTRA_OPTIONS)!!
        cardScanner.cameraCardScanner = paymentOptions.features.cameraCardScanner
    }

    private fun setupCardsPager() {
        cardsPagerAdapter = CardsViewPagerAdapter(requireActivity(), paymentOptions)
        viewPager.adapter = cardsPagerAdapter.apply {
            canScanCard = cardScanner.cardScanAvailable
            scanButtonListener = this@PaymentFragment
        }
        pagerIndicator.attachToPager(viewPager)
        pagerIndicator.setOnPageChangeListener(object : ScrollingPagerIndicator.OnPageChangeListener {
            override fun onChange(currentItem: Int) {
                viewPagerPosition = currentItem
            }
        })
    }

    private fun setupFpsButton() {
        fpsButton.visibility = View.VISIBLE
        if (paymentOptions.features.localizationSource is AsdkSource &&
                (paymentOptions.features.localizationSource as AsdkSource).language != Language.RU) {
            fpsButton.findViewById<ImageView>(R.id.acq_button_fps_logo_with_text).visibility = View.GONE
            fpsButton.findViewById<ViewGroup>(R.id.acq_button_fps_logo_en).visibility = View.VISIBLE
        }
        fpsButton.setOnClickListener {
            hideSystemKeyboard()
            paymentViewModel.startFpsPayment(paymentOptions)
        }
    }

    private fun loadCards() {
        val recurrentPayment = paymentOptions.order.recurrentPayment
        val handleCardsErrorInSdk = paymentOptions.features.handleCardListErrorInSdk
        paymentViewModel.getCardList(handleCardsErrorInSdk, customerKey, recurrentPayment)
    }

    private fun observeLiveData() {
        with(paymentViewModel) {
            cardsResultLiveData.observe(viewLifecycleOwner, Observer { handleCardsResult(it) })
            screenStateLiveData.observe(viewLifecycleOwner, Observer { handleScreenState(it) })
        }
    }

    private fun handleScreenState(screenState: ScreenState) {
        if (screenState is ErrorButtonClickedEvent) {
            loadCards()
        }
    }

    private fun handleCardsResult(cards: List<Card>) {
        val cardsList = cards.toMutableList()
            val selectedCard = cardsList.find { it.cardId == selectedCardId }
            if (selectedCard != null && cardsList.remove(selectedCard)) {
                cardsList.add(0, selectedCard)
        }

        cardsPagerAdapter.cardList = cardsList
        viewPager.setCurrentItem(viewPagerPosition, false)
        pagerIndicator.reattach()
        pagerIndicator.visibility = if (cards.isEmpty()) View.GONE else View.VISIBLE

        if (asdkState is RejectedState) {
            if (!rejectedDialogDismissed) {
                if (rejectedDialog == null) showRejectedDialog()
            } else {
                showRejectedCard()
            }
        }
    }

    private fun processCardPayment() {
        val emailRequired = paymentOptions.features.emailRequired
        val emailText = emailEditText.text.toString()
        val email = when {
            emailEditText.visibility != View.VISIBLE -> null
            emailRequired || (!emailRequired && emailText.isNotBlank()) -> emailText
            else -> null
        }

        val paymentSource = cardsPagerAdapter.getSelectedPaymentSource(viewPager.currentItem)

        if (validateInput(paymentSource, email)) {
            when (val state = asdkState) {
                is SelectCardAndPayState -> paymentViewModel.finishPayment(state.paymentId, paymentSource, email)
                else -> paymentViewModel.startPayment(paymentOptions, paymentSource, email)
            }
        }
    }

    private fun hideSystemKeyboard() {
        (requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(this.requireView().applicationWindowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    private fun modifySpan(amount: String): CharSequence {
        val amountSpan = SpannableString(amount)
        val commaIndex = amount.indexOf(",")

        return if (commaIndex < 0) {
            amount
        } else {
            val coinsColor = ContextCompat.getColor(requireContext(), R.color.acq_colorCoins)
            amountSpan.setSpan(ForegroundColorSpan(coinsColor), commaIndex + 1, amount.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            amountSpan
        }
    }

    private fun showRejectedDialog() {
        rejectedDialog = AlertDialog.Builder(activity).apply {
            setTitle(localization.payDialogCvcMessage)
            setCancelable(false)
            setPositiveButton(localization.payDialogCvcAcceptButton) { _, _ ->
                showRejectedCard()
                rejectedDialogDismissed = true
            }
        }.show()
    }

    private fun showRejectedCard() {
        val position = cardsPagerAdapter.getCardPosition((asdkState as RejectedState).cardId)
        viewPager.currentItem = position ?: 0
        cardsPagerAdapter.setRejectedCard(position)
    }

    private fun getSavedCardOptions(): SavedCardsOptions {
        return SavedCardsOptions().setOptions {
            setTerminalParams(paymentOptions.terminalKey, paymentOptions.password, paymentOptions.publicKey)
            customer = paymentOptions.customer
            features = paymentOptions.features.apply {
                showOnlyRecurrentCards = paymentOptions.order.recurrentPayment
                this@PaymentFragment.selectedCardId?.let { selectedCardId = it }
            }
        }
    }

    private fun createTextChangeListener(): TextWatcher {
        return object : TextWatcher {

            override fun afterTextChanged(s: Editable?) = Unit

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.isEmpty() && emailHintTextView.visibility == View.VISIBLE) {
                    hideEmailHint()
                } else if (s.isNotEmpty() && emailHintTextView.visibility == View.INVISIBLE) {
                    showEmailHint()
                }
            }
        }
    }

    private fun hideEmailHint() {
        ObjectAnimator.ofFloat(emailHintTextView, View.ALPHA, 1f, 0f).apply {
            duration = EMAIL_HINT_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
        }.apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    emailHintTextView.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator?) {
                    emailHintTextView.visibility = View.INVISIBLE
                }
            })
        }.start()
    }

    private fun showEmailHint() {
        ObjectAnimator.ofFloat(emailHintTextView, View.ALPHA, 0f, 1f).apply {
            duration = EMAIL_HINT_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
        }.apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    emailHintTextView.visibility = View.VISIBLE
                }
            })
        }.start()
    }
}