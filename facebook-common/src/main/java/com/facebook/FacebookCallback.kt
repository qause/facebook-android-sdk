/*
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.facebook

/** A callback class for the Facebook SDK. */
interface FacebookCallback<RESULT> {
  /**
   * Called when the dialog completes without error.
   *
   * Note: This will be called instead of [.onCancel] if any of the following conditions are true.
   *
   * * [com.facebook.share.widget.MessageDialog] is used.
   * * The logged in Facebook user has not authorized the app that has initiated the dialog.
   *
   * @param result Result from the dialog
   */
  fun onSuccess(result: RESULT)

  /**
   * Called when the dialog is canceled.
   *
   * Note: [.onSuccess] will be called instead if any of the following conditions are true.
   *
   * * [com.facebook.share.widget.MessageDialog] is used.
   * * The logged in Facebook user has not authorized the app that has initiated the dialog.
   */
  fun onCancel()

  /**
   * Called when the dialog finishes with an error.
   *
   * @param error The error that occurred
   */
  fun onError(error: FacebookException)
}
