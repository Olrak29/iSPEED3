package com.olrak.ispeed.dashboard

import android.view.MenuItem
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.olrak.ispeed.R
import com.olrak.ispeed.app.foundation.BaseFragment
import com.olrak.ispeed.app.shared.extension.showFancyToast
import com.olrak.ispeed.app.shared.widget.DialogFactory
import com.olrak.ispeed.app.shared.widget.DialogFactory.Companion.showCustomInfoDialog
import com.olrak.ispeed.databinding.FragmentDashboardBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DashboardFragment : BaseFragment<FragmentDashboardBinding>(bindingInflater = FragmentDashboardBinding::inflate) {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onViewCreated() {
        super.onViewCreated()
        with(binding) {
            setupComponents()
            setupObserver()
        }
    }

    private fun setupObserver() {
        with(viewModel) {
            userDetails.observe(viewLifecycleOwner) {
                binding.toolBar.txtUserToolbar.text = "${it.firstName} ${it.lastName}"
            }
        }
    }

    private fun FragmentDashboardBinding.setupComponents() {
        cvSpeedTest.setOnClickListener {
            findNavController().navigate(directions = DashboardFragmentDirections.navigateToSpeedTestFragment())
        }

        cvStab.setOnClickListener {
            findNavController().navigate(directions = DashboardFragmentDirections.navigateToAutomaticTrackFragment())
        }

        cvGeomap.setOnClickListener {
            findNavController().navigate(directions = DashboardFragmentDirections.navigateToGeoMapFragment())
        }

        instructionLayout.setOnClickListener {
            showCustomInfoDialog(
                context = requireContext(),
                dialogAttributes = DialogFactory.InformationDialogAttributes(
                    title = "INSTRUCTIONS",
                    header = "How to use this service?",
                    firstLineTitle = requireContext().getString(R.string.instructions_manual_tracking),
                    secondLineTitle = requireContext().getString(R.string.instructions_automatic_tracking),
                    thirdLineTitle = requireContext().getString(R.string.instructions_geo_map_tracking)
                )
            )
        }

        aboutLayout.setOnClickListener {
            showCustomInfoDialog(
                context = requireContext(),
                dialogAttributes = DialogFactory.InformationDialogAttributes(
                    title = "ABOUT",
                    header = "Purpose of each Testing Services",
                    firstLineTitle = "This service will manually track your internet connectivity capturing its ping, download, and upload speed.",
                    secondLineTitle = "This service will provide real-time and compiled reports of the user’s internet stability status. The graph will show the unstable/stable reports gathered during testing that are sectioned through timely synchronization (Days, Weeks, Months). The table below shows the continuous report of the user’s internet stability status.",
                    thirdLineTitle = "This service will provide reports regarding the behavior of user’s internet connectivity in their current location."
                )
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.logout) {
            viewModel.logoutUser()
            showFancyToast(message = "Logging Out")
        }
        return false
    }
}