package com.qure.mypage

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kakao.sdk.user.UserApiClient
import com.qure.core.BaseFragment
import com.qure.core.extensions.Empty
import com.qure.core.util.FishingMemoryToast
import com.qure.core.util.setOnSingleClickListener
import com.qure.domain.repository.AuthRepository
import com.qure.mypage.databinding.FragmentMyPageBinding
import com.qure.navigator.LoginNavigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MyPageFragment : BaseFragment<FragmentMyPageBinding>(R.layout.fragment_my_page) {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var loginNavigator: LoginNavigator

    private val viewModel by viewModels<MyPageViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        observe()
    }

    private fun initView() {
        try {
            val pInfo =
                context?.packageManager?.getPackageInfo(context?.packageName ?: String.Empty, 0)
            val versionName = pInfo?.versionName
            binding.textViewFragmentMypageVersionName.text = versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        binding.textViewFragmentMypageKakaoEmail.text = authRepository.getEmailFromLocal()

        binding.textViewFragmentMypageLogout.setOnSingleClickListener {
            onLogoutButtonClicked()
        }

        binding.textViewFragmentMypageWithdrawalService.setOnSingleClickListener {
            withdrawService()
        }
    }

    private fun observe() {
        viewModel.error
            .onEach { errorMessage -> FishingMemoryToast().error(requireContext(), errorMessage) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.logutSucceed.collect { logoutSucceed ->
                        if (logoutSucceed) {
                            startActivity(loginNavigator.intent(requireContext()))
                            activity?.finish()
                        }
                    }
                }
            }
        }

    }

    private fun withdrawService() {
        UserApiClient.instance.unlink {throwable ->
            if (throwable != null) {
                FishingMemoryToast().error(requireContext(), throwable.message)
            } else {

            }
        }
    }

    private fun onLogoutButtonClicked() {
        viewModel.logoutUser()
    }
}