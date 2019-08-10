package com.fireblade.effectivecamera.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.fireblade.effectivecamera.R

class PermissionsFragment : Fragment() {

  private val requestCamera = 2

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (!hasPermissions(requireContext())) {
      requestPermissions(arrayOf(
        Manifest.permission.CAMERA), requestCamera)
    } else {
      Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
        PermissionsFragmentDirections.actionPermissionsToCamera()
      )
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == requestCamera) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
          PermissionsFragmentDirections.actionPermissionsToCamera()
        )
      } else {
        Toast.makeText(context, "Permission is denied for Camera", Toast.LENGTH_SHORT).show()
      }
    }
  }

  companion object {

    fun hasPermissions(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }
}