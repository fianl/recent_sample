package com.bbros.sayup.model.user

import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModel
import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.bbros.sayup.activity.BaseActivity
import com.bbros.sayup.enumeration.LoginType
import com.bbros.sayup.firebase.CloudMessagingManager
import com.bbros.sayup.local.db.realm.disk.AllowedADPushRealmManager
import com.bbros.sayup.marketing.AdbrixAnalyticsManager
import com.bbros.sayup.marketing.AdjustAnalyticsManager
import com.bbros.sayup.marketing.FirebaseAnalyticsManager
import com.bbros.sayup.model.*
import com.bbros.sayup.model.qrcode.QrCodeFavorites
import com.bbros.sayup.network.Retro
import com.bbros.sayup.network.requestbody.PolicyBody
import com.bbros.sayup.util.parseNetworkError
import com.bbros.sayup.util.showToast
import com.orhanobut.logger.Logger
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class UserSignInViewModel() : ViewModel() {
    private lateinit var policies: ArrayList<PolicyBody>
    private var loginType = LoginType.LOCAL
    private var successUserSignIn = false
    private val isEmailFirstClick = ObservableBoolean(true)
    private val _userEmail = ObservableField<String>("")
    val userEmail: ObservableField<String>
        get() = _userEmail

    private val _userEmailAlert = ObservableField<String>("")
    val userEmailAlert: ObservableField<String>
        get() = _userEmailAlert

    private val _emailExplainVisible = ObservableBoolean(false)
    val emailExplainVisible: ObservableBoolean
        get() = _emailExplainVisible

    private val _isEmailValidate = ObservableBoolean(false)
    val isEmailValidate: ObservableBoolean
        get() = _isEmailValidate

    private val _cellphone = ObservableField<String>("")
    val cellphone: ObservableField<String>
        get() = _cellphone

    private val _userName = ObservableField<String>("")
    val userName: ObservableField<String>
        get() = _userName

    private val _confirmVisible = ObservableBoolean(false)
    val confirmVisible: ObservableBoolean
        get() = _confirmVisible

    private val _isExpanded = ObservableBoolean(false)
    val isExpanded: ObservableBoolean
        get() = _isExpanded

    private val _passwordVisible = ObservableBoolean(false)
    val passwordVisible: ObservableBoolean
        get() = _passwordVisible

    private val _isPasswordValidate = ObservableBoolean(false)
    val isPasswordValidate: ObservableBoolean
        get() = _isPasswordValidate

    private val _isPassConfirmValidate = ObservableBoolean(false)
    val isPassConfirmValidate: ObservableBoolean
        get() = _isPassConfirmValidate

    private val _isNameInput = ObservableBoolean(false)
    private val _showNameAlert = ObservableBoolean(false)
    val showNameAlert: ObservableBoolean
        get() = _showNameAlert

    private val _isCellPhoneCertification = ObservableBoolean(false)
    val isCellPhoneCertification: ObservableBoolean
        get() = _isCellPhoneCertification

    val toastMessage = ObservableField<String>("")
    val isShowProgressDialog = ObservableBoolean(false)
    val loggedInUser = ObservableField<User>()

    fun setIsEmailFirstClick(isFirst: Boolean) {
        isEmailFirstClick.set(isFirst)
    }

    fun getIsEmailFirstClick(): Boolean {
        return isEmailFirstClick.get()
    }

    fun setIsExpanded(isExpanded: Boolean) {
        _isExpanded.set(isExpanded)
        setEmailExplainVisible()

        if (loginType == LoginType.LOCAL)
            _passwordVisible.set(true)
        else {
            _passwordVisible.set(false)
        }
    }

    fun setEmailExplainVisible() {
        _emailExplainVisible.set(_isExpanded.get() && userEmail.get().isNullOrEmpty())
    }

    fun setUserEmailAlertContent(content: String?) {
        _userEmailAlert.set(content)
    }

    fun setEmailValidation(isPatternValidate: Boolean) {
        if (isPatternValidate && loginType == LoginType.LOCAL) {
            getIsExistEmail()
        } else {
            _isEmailValidate.set(isPatternValidate)
            setConfirmVisible()
        }
    }

    fun setPasswordValidate(isValidate: Boolean) {
        _isPasswordValidate.set(isValidate)
        setConfirmVisible()
    }

    fun setPassConfirmValidate(isValidate: Boolean) {
        _isPassConfirmValidate.set(isValidate)
        setConfirmVisible()
    }

    fun setNameInput(isInput: Boolean) {
        _isNameInput.set(isInput)
        setConfirmVisible()
    }

    fun setCellphonCertificate(isCertificate: Boolean) {
        _isCellPhoneCertification.set(isCertificate)
        setConfirmVisible()
    }

    private fun setConfirmVisible() {
        _confirmVisible.set(_isEmailValidate.get() && _isPasswordValidate.get() && _isPassConfirmValidate.get() && _isNameInput.get() && _isCellPhoneCertification.get())
    }

    fun setPolicies(policies: ArrayList<PolicyBody>) {
        this.policies = policies
    }

    fun setOnlyLoginType(loginType: LoginType) {
        this.loginType = loginType
        requestPutSNSRegisterAgreement()
    }

    fun setSNSLoginType(loginType: LoginType, snsToken: String) {
        requestSNSLogin(loginType, snsToken)
    }

    fun requestRemoveSnsLoginUser() {
        if (loginType != LoginType.LOCAL)
            requestDeleteSNSUser()
    }

    fun requestRegister(password: String? = null) {
        if (loginType == LoginType.LOCAL && !password.isNullOrEmpty()) {
            registerEmailUser(password)
        } else {
            registerSNSUser()
        }
    }

    // Email 회원 가입
    @SuppressLint("CheckResult")
    private fun registerEmailUser(password: String) {
        Retro.instance.loginService.registerEmailUser(RegisterUser(_userEmail.get(), password, _userName.get(), _cellphone.get()?.replace("-", ""),
                _isCellPhoneCertification.get(), null, policy = policies))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    DdocDocToken.saveToken(it.token)
                    successUserSignIn = true
                    requestUserInfo()
                }, {
                    isShowProgressDialog.set(false)
                    toastMessage.set(it.parseNetworkError()?.message ?: "")
                })

    }

    /**
     *  SNS 회원가입
     *  추천인 코드가 있을 경우에는 registerApi & recommendCodeApi
     *  추천인 코드가 없을 경우에는 registerApi
     */
    @SuppressLint("CheckResult")
    private fun registerSNSUser() {
        Retro.instance.userService.putUserInfo(UserInfoBody(email = _userEmail.get(), name = _userName.get(), phone = _cellphone.get()?.replace("-", ""),
                phoneCheck = _isCellPhoneCertification.get())).toSingleDefault("success")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({
                    successUserSignIn = true
                    requestUserInfo()
                }, { err ->
                    err.printStackTrace()
                })
    }

    // SNS login api
    @SuppressLint("CheckResult")
    private fun requestSNSLogin(loginType: LoginType, token: String) {
        this.loginType = loginType
        Retro.instance.loginService.snsLogin(loginType.provider, AccessToken(token))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    isShowProgressDialog.set(true)
                    requestUserInfo(it.token)
                }, {
                    toastMessage.set(it.parseNetworkError()?.message ?: "")
                })
    }

    // 유저 정보
    @SuppressLint("CheckResult")
    private fun requestUserInfo(token: String? = null) {
        if (token != null)
            DdocDocToken.saveToken(token)

        UserManager().getUserInfo()
                .subscribe({
                    if (token.isNullOrEmpty()) {
                        FirebaseAnalyticsManager.registeredUser(it.getLoginType())
                        AdbrixAnalyticsManager.registeredUser(it.getLoginType())
                        AdjustAnalyticsManager.sendEvent("85ic65")
                        Logger.i("회원가입!!!!!!!!!!!!!!!!!!")
                    }

                    QrCodeFavorites.init()

                    CloudMessagingManager.deleteFCMToken()
                    // fcm token 등록
                    CloudMessagingManager.registerFCMToken(AllowedADPushRealmManager().checkAllowedADPush())
                            .subscribe({ Logger.d("success") }, { it.printStackTrace() })
                    isShowProgressDialog.set(false)
                    loggedInUser.set(it)
                }, {
                    isShowProgressDialog.set(false)
                })
    }

    fun requestSnsUSerAgreement() {
        requestPutSNSRegisterAgreement()
    }

    // SNS 사용자 동의 등록
    @SuppressLint("CheckResult")
    private fun requestPutSNSRegisterAgreement() {
        Retro.instance.loginService.putSNSRegisterAgreement(RegisterUser(policy = policies))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Logger.d("sns_user_agreement")
                }, {
                    Logger.e(it.parseNetworkError()?.message ?: "")
                })
    }

    // SNS 회원 탈퇴
    @SuppressLint("CheckResult")
    private fun requestDeleteSNSUser() {
        Retro.instance.loginService.deleteUserWithDraw()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate { }
                .subscribe({
                    UserManager.user!!.getLoginAccount().deleteLoginType()
                    UserManager.requestLogOut()
                }, { toastMessage.set(it.toString()) })
    }

    // 가입된 이메일인지 확인
    @SuppressLint("CheckResult")
    fun getIsExistEmail() {
        Retro.instance.loginService.getIsExistEmail(EmailExistRequestModel(_userEmail.get() ?: ""))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({ result ->
                    if (result.existEmail) {
                        setUserEmailAlertContent("이미 가입된 이메일 주소 입니다.")
                        _isEmailValidate.set(false)
                    } else {
                        setUserEmailAlertContent("")
                        _isEmailValidate.set(true)
                    }

                    setConfirmVisible()
                }, {
                    setUserEmailAlertContent(it.parseNetworkError()?.message)
                    _isEmailValidate.set(false)
                })
    }

    fun getIsSuccessSignIn() = successUserSignIn
    fun getUserLoginType() = loginType
}