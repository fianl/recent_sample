package com.bbros.sayup.activity

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Intent
import android.databinding.DataBindingUtil
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.Log
import android.view.*
import com.bbros.sayup.R
import com.bbros.sayup.base.Navigator
import com.bbros.sayup.databinding.ActivityUserSigninBinding
import com.bbros.sayup.dialog.SimpleMessageDialog
import com.bbros.sayup.enumeration.LoginType
import com.bbros.sayup.fragment.login.FacebookLoginManager
import com.bbros.sayup.fragment.login.KakaoLoginManager
import com.bbros.sayup.fragment.login.NaverLoginManager
import com.bbros.sayup.listener.SNSLoginListener
import com.bbros.sayup.marketing.FirebaseAnalyticsManager
import com.bbros.sayup.model.User
import com.bbros.sayup.model.user.UserSignInViewModel
import com.bbros.sayup.util.addUnderLineSpan
import com.jakewharton.rxbinding2.widget.textChanges
import com.orhanobut.logger.Logger
import java.lang.ClassCastException
import java.util.concurrent.TimeUnit

/**
 * Created by mark on 2019-09-26.
 *
 */
class UserSignInActivity : BaseActivity(), SNSLoginListener {
    private lateinit var binding: ActivityUserSigninBinding
    private lateinit var viewModel: UserSignInViewModel

    private val kakaoManager = KakaoLoginManager(this)
    private val facebookManager = FacebookLoginManager(this)
    private val naverManager = NaverLoginManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(Window.FEATURE_NO_TITLE)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_user_signin)
        viewModel = UserSignInViewModel()
        binding.signin = viewModel
        viewModel.setPolicies(intent.getParcelableArrayListExtra("policy"))

        uiInit()
    }

    private fun uiInit() {
        if(Build.VERSION.SDK_INT >= 21){
            window.statusBarColor = Color.TRANSPARENT
        }

        val sb = SpannableStringBuilder("재인증")
        sb.addUnderLineSpan()
        binding.tvReCertificate.text = sb

        kakaoManager.loginListener = this
        facebookManager.loginListener = this
        naverManager.loginListener = this

        binding.etEmail.inputType = InputType.TYPE_NULL

        binding.viewSignInTopSpace.setOnClickListener {
            onBackPressed()
        }

        binding.ivSignInClose.setOnClickListener {
            FirebaseAnalyticsManager.sendCustomEvent("signup_step3_cancel")
            onBackPressed()
        }

        binding.etEmail.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus && viewModel.getIsEmailFirstClick()){
                binding.clSnsSignInContainer.visibility = View.INVISIBLE
                val topValueAni = ValueAnimator.ofInt(binding.clSignInContainer.top, 0)
                topValueAni.duration = 300
                topValueAni.addUpdateListener { ani ->
                    binding.clSignInContainer.top = ani.animatedValue as Int
                }

                topValueAni.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator?) {}
                    override fun onAnimationEnd(animation: Animator?) {
                        val params = binding.clSignInContainer.layoutParams
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT
                        binding.clSignInContainer.layoutParams = params
                        viewModel.setIsExpanded(true)
                        viewModel.setIsEmailFirstClick(false)
                        binding.clSignInContainer.setBackgroundColor(Color.WHITE)
                        binding.clBottomSignInMain.setBackgroundColor(Color.WHITE)
                        binding.etEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        binding.etEmail.isCursorVisible = true
                        binding.etEmail.setText("")

                        showSoftKeyBoard(binding.etEmail)

                        FirebaseAnalyticsManager.sendCustomEvent("signup_step2_email")
                    }

                    override fun onAnimationCancel(animation: Animator?) {}

                    override fun onAnimationStart(animation: Animator?) {}
                })

                topValueAni.start()
            }

            if(hasFocus)
                binding.viewEmailUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.main_black_2c3744))
            else
                binding.viewEmailUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.gray20))
        }

        binding.clCellphoneContainer.setOnClickListener {
            FirebaseAnalyticsManager.sendCustomEvent("signup_step3_phone_number")
            navi.goCertificate()
        }

        addApiDisposable(binding.etEmail.textChanges().throttleLast(500, TimeUnit.MILLISECONDS).subscribe {
            viewModel.setEmailExplainVisible()
            if (it?.toString().isNullOrEmpty()) {
                viewModel.setUserEmailAlertContent("")
                viewModel.setEmailValidation(false)
            } else {
                viewModel.setUserEmailAlertContent("이메일 형식이 올바르지 않습니다.")
                viewModel.setEmailValidation(android.util.Patterns.EMAIL_ADDRESS.matcher(it.toString()).matches())
            }
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.toString().isNullOrEmpty()) {
                    binding.tvPasswordAlert.text = ""
                } else {
                    binding.tvPasswordAlert.text = "비밀번호 (영문,숫자조합 6~20자)"
                    viewModel.setPasswordValidate(s.toString().length in 6..20 && checkPasswordValidation(s.toString()))
                    viewModel.setPassConfirmValidate(s.toString() == binding.etPassConfirm.text.toString())
                }
            }
        })

        binding.etPassword.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus) {
                binding.viewPasswordUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.main_black_2c3744))
                FirebaseAnalyticsManager.sendCustomEvent("signup_step3_password")
            } else
                binding.viewPasswordUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.gray20))
        }

        binding.etPassConfirm.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.toString().isNullOrEmpty()) {
                    binding.tvPassConfirmAlert.text = ""
                } else {
                    binding.tvPassConfirmAlert.text = "비밀번호가 맞지 않습니다."
                    viewModel.setPassConfirmValidate(s.toString() == binding.etPassword.text.toString())
                }
            }

        })

        binding.etPassConfirm.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus)
                binding.viewPasswordConfirmUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.main_black_2c3744))
            else
                binding.viewPasswordConfirmUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.gray20))
        }

        binding.etName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.showNameAlert.set(s?.length?:0 == 1)

                if(s?.length?: 0 == 1){
                    viewModel.showNameAlert.set(true)
                }else{
                    viewModel.showNameAlert.set(!checkNameValidation(s.toString()) && s?.length?: 0 != 0)
                    viewModel.setNameInput(checkNameValidation(s.toString()) && s?.length?: 0 > 1)
                }
            }
        })

        binding.etName.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus) {
                binding.viewNameUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.main_black_2c3744))
                FirebaseAnalyticsManager.sendCustomEvent("signup_step3_name")
            } else
                binding.viewNameUnderLine.setBackgroundColor(ContextCompat.getColor(this, R.color.gray20))
        }

        binding.tvCellphone.addTextChangedListener(PhoneNumberFormattingTextWatcher())

        binding.tvBottomSignInConfirm.setOnClickListener {
            if(binding.etPassword.text.toString() == binding.etPassConfirm.text.toString()) {
                FirebaseAnalyticsManager.sendCustomEvent("signup_step3_complete")
                viewModel.requestRegister(binding.etPassword.text.toString())
            } else {
                viewModel.setPassConfirmValidate(false)
            }
        }

        binding.flKakaoSignIn.setOnClickListener {
            FirebaseAnalyticsManager.sendCustomEvent("signup_step2_kakao")
            kakaoManager.login()
        }
        binding.flNaverSignin.setOnClickListener {
            FirebaseAnalyticsManager.sendCustomEvent("signup_step2_naver")
            naverManager.login()
        }
        binding.flFacebookSignIn.setOnClickListener {
            FirebaseAnalyticsManager.sendCustomEvent("signup_step2_facebook")
            facebookManager.login()
        }

        val loginType = ((intent.getSerializableExtra("loginType"))?: LoginType.LOCAL) as LoginType
        if(loginType != LoginType.LOCAL){
            setSignInInfoFromSns(intent.getSerializableExtra("loginType") as LoginType, intent.getStringExtra("email")?: "", intent.getStringExtra("name")?: "")
        }

        viewModel.toastMessage.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback(){
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                try{
                    val message = (sender as ObservableField<*>).get().toString()

                    if(!message.isNullOrEmpty()) {
                        showToast(message)
                        viewModel.toastMessage.set("")
                    }
                }catch (e: ClassCastException){
                    Logger.e(e.message?: "")
                }
            }
        })

        viewModel.isShowProgressDialog.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback(){
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                try{
                    val isShow = (sender as ObservableBoolean).get()
                    if(isShow)
                        showProgressDialog()
                    else
                        dismissProgressDialog()

                }catch (e: Exception){
                    Logger.e(e.message?: "")
                }
            }
        })

        viewModel.loggedInUser.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback(){
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                try{
                    val loginUser = (sender as ObservableField<*>).get() as User

                    if(viewModel.getUserLoginType() != LoginType.LOCAL && !viewModel.getIsSuccessSignIn()) {
                        if(loginUser.active){
                            finishActivity()
                            Navigator.goMain(this@UserSignInActivity)
                        }else {
                            setSignInInfoFromSns(viewModel.getUserLoginType(), loginUser.getEmail().value?: "", loginUser.name?: "")
                            viewModel.setPasswordValidate(true)
                            viewModel.setPassConfirmValidate(true)
                        }
                    }else{
                        finishActivity()
                        Navigator(this@UserSignInActivity).goSignInComplete()
                    }

                }catch (e: Exception){
                    Logger.e(e.message?: "")
                }
            }
        })
    }

    fun setSignInInfoFromSns(loginType: LoginType, email: String, name: String) {
        val params = binding.clSignInContainer.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        binding.clSignInContainer.layoutParams = params

        viewModel.setOnlyLoginType(loginType)
        viewModel.setIsExpanded(true)
        viewModel.setPasswordValidate(true)
        viewModel.setPassConfirmValidate(true)

        binding.clSignInContainer.setBackgroundColor(Color.WHITE)
        binding.clBottomSignInMain.setBackgroundColor(Color.WHITE)

        binding.etEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        viewModel.userEmail.set(email)
        viewModel.userName.set(name)

        viewModel.requestSnsUSerAgreement()
    }

    private fun checkPasswordValidation(password: String): Boolean{
        val alphabetMatch = password.matches(Regex(".*[a-zA-Z]+.*"))
        val numberMatch = password.matches(Regex(".*[0-9].*"))
        val koreanMatch = password.matches(Regex(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*"))

        return alphabetMatch && numberMatch && !koreanMatch
    }

    private fun checkNameValidation(name: String): Boolean {
        if(name.isEmpty())
            return false

        val numberMatch = name.matches(Regex(".*[0-9].*"))
        val specialCharMatch = name.matches(Regex(".*\\W\\s.*"))

        return !numberMatch && !specialCharMatch
    }

    override fun onSuccess(loginType: LoginType, token: String) {
        viewModel.setSNSLoginType(loginType, token)
    }

    override fun onFailed(error: String?) {
        Logger.e("error message: $error")
        showToast("SNS 인증과정에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == Navigator.REQUEST_CELLPHONE_CERTIFICATE && data?.extras?.isEmpty == false) {
            binding.tvCellphone.text = data?.getStringExtra("cellphone")?: ""
            viewModel.cellphone.set(data?.getStringExtra("cellphone")?: "")
            viewModel.setCellphonCertificate(data?.getBooleanExtra("certificate", false)?: false)
        }else {
            kakaoManager.onActivityResult(requestCode, resultCode, data)
            facebookManager.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        if(viewModel.isExpanded.get()) {
            SimpleMessageDialog(this, "회원가입 취소",
                    "가입이 완료되지 않았어요.\n정말 취소하겠어요?",
                    "예", View.OnClickListener {
                FirebaseAnalyticsManager.sendCustomEvent("signup_step3_cancel_yes")
                viewModel.requestRemoveSnsLoginUser()
                finishActivity()
                overridePendingTransition(0, R.anim.slide_down)
            }, "아니오", View.OnClickListener {
                FirebaseAnalyticsManager.sendCustomEvent("signup_step3_cancel_no")
            }).show()
        }else{
            finishActivity()
            overridePendingTransition(0, R.anim.slide_down)
        }
    }

    override fun onDestroy() {
        kakaoManager.onDestroy()
        facebookManager.onDestroy()

        super.onDestroy()
    }
}