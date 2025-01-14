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

package com.facebook.share.model;

import android.net.Uri;
import android.os.Parcel;
import android.util.Log;
import androidx.annotation.Nullable;
import com.facebook.internal.qualityvalidation.Excuse;
import com.facebook.internal.qualityvalidation.ExcusesForDesignViolations;

/**
 * Describes link content to be shared.
 *
 * <p>Use {@link ShareLinkContent.Builder} to build instances.
 *
 * <p>See documentation for <a
 * href="https://developers.facebook.com/docs/sharing/best-practices">best practices</a>.
 */
// TODO: (T24423331) remove all deprecated methods. No longer work and past 90day window in November
@ExcusesForDesignViolations(@Excuse(type = "MISSING_UNIT_TEST", reason = "Legacy"))
public final class ShareLinkContent
    extends ShareContent<ShareLinkContent, ShareLinkContent.Builder> {
  @Deprecated private final String contentDescription;
  @Deprecated private final String contentTitle;
  @Deprecated private final Uri imageUrl;
  private final String quote;

  private ShareLinkContent(final Builder builder) {
    super(builder);
    this.contentDescription = builder.contentDescription;
    this.contentTitle = builder.contentTitle;
    this.imageUrl = builder.imageUrl;
    this.quote = builder.quote;
  }

  ShareLinkContent(final Parcel in) {
    super(in);
    this.contentDescription = in.readString();
    this.contentTitle = in.readString();
    this.imageUrl = in.readParcelable(Uri.class.getClassLoader());
    this.quote = in.readString();
  }

  /**
   * @deprecated As of Graph API 2.9 this field is deprecated and may not function as expected. For
   *     more information, see
   *     https://developers.facebook.com/docs/apps/changelog#v2_9_deprecations. The description of
   *     the link. If not specified, this field is automatically populated by information scraped
   *     from the link, typically the title of the page.
   * @return The description of the link.
   */
  @Deprecated
  public String getContentDescription() {
    return this.contentDescription;
  }

  /**
   * @deprecated As of Graph API 2.9 this field is deprecated and may not function as expected. For
   *     more information, see
   *     https://developers.facebook.com/docs/apps/changelog#v2_9_deprecations. The title to display
   *     for this link.
   * @return The link title.
   */
  @Deprecated
  @Nullable
  public String getContentTitle() {
    return this.contentTitle;
  }

  /**
   * @deprecated As of Graph API 2.9 this field is deprecated and may not function as expected. For
   *     more information, see
   *     https://developers.facebook.com/docs/apps/changelog#v2_9_deprecations. The URL of a picture
   *     to attach to this content.
   * @return The network URL of an image.
   */
  @Deprecated
  @Nullable
  public Uri getImageUrl() {
    return this.imageUrl;
  }

  /**
   * The quoted text to display for this link.
   *
   * @return The text quoted from the link.
   */
  @Nullable
  public String getQuote() {
    return this.quote;
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(final Parcel out, final int flags) {
    super.writeToParcel(out, flags);
    out.writeString(this.contentDescription);
    out.writeString(this.contentTitle);
    out.writeParcelable(this.imageUrl, 0);
    out.writeString(this.quote);
  }

  @SuppressWarnings("unused")
  public static final Creator<ShareLinkContent> CREATOR =
      new Creator<ShareLinkContent>() {
        public ShareLinkContent createFromParcel(final Parcel in) {
          return new ShareLinkContent(in);
        }

        public ShareLinkContent[] newArray(final int size) {
          return new ShareLinkContent[size];
        }
      };

  /** Builder for the {@link ShareLinkContent} interface. */
  public static final class Builder extends ShareContent.Builder<ShareLinkContent, Builder> {
    static final String TAG = Builder.class.getSimpleName();
    @Deprecated private String contentDescription;
    @Deprecated private String contentTitle;
    @Deprecated private Uri imageUrl;

    private String quote;

    /**
     * @deprecated As of Graph API 2.9 this field is deprecated and may not function as expected.
     *     Set the contentDescription of the link. For more information, see
     *     https://developers.facebook.com/docs/apps/changelog#v2_9_deprecations.
     * @param contentDescription The contentDescription of the link.
     * @return The builder.
     */
    @Deprecated
    public Builder setContentDescription(@Nullable final String contentDescription) {
      Log.w(TAG, "This method does nothing. ContentDescription is deprecated in Graph API 2.9.");
      return this;
    }

    /**
     * @deprecated As of Graph API 2.9 this field is deprecated and may not function as expected.
     *     For more information, see
     *     https://developers.facebook.com/docs/apps/changelog#v2_9_deprecations. Set the
     *     contentTitle to display for this link.
     * @param contentTitle The link contentTitle.
     * @return The builder.
     */
    @Deprecated
    public Builder setContentTitle(@Nullable final String contentTitle) {
      Log.w(TAG, "This method does nothing. ContentTitle is deprecated in Graph API 2.9.");
      return this;
    }

    /**
     * @deprecated As of Graph API 2.9 this field is deprecated and may not function as expected.
     *     For more information, see
     *     https://developers.facebook.com/docs/apps/changelog#v2_9_deprecations. Set the URL of a
     *     picture to attach to this content.
     * @param imageUrl The network URL of an image.
     * @return The builder.
     */
    @Deprecated
    public Builder setImageUrl(@Nullable final Uri imageUrl) {
      Log.w(TAG, "This method does nothing. ImageUrl is deprecated in Graph API 2.9.");
      return this;
    }

    /**
     * Set the quote to display for this link.
     *
     * @param quote The text quoted from the link.
     * @return The builder.
     */
    public Builder setQuote(@Nullable final String quote) {
      this.quote = quote;
      return this;
    }

    @Override
    public ShareLinkContent build() {
      return new ShareLinkContent(this);
    }

    @Override
    public Builder readFrom(final ShareLinkContent model) {
      if (model == null) {
        return this;
      }
      return super.readFrom(model)
          .setContentDescription(model.getContentDescription())
          .setImageUrl(model.getImageUrl())
          .setContentTitle(model.getContentTitle())
          .setQuote(model.getQuote());
    }
  }
}
