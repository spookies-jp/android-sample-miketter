package jp.co.spookies.android.miketter;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * twitterのOAuth認証アクティビティ
 */
public class LoginActivity extends Activity {
	private static final String CALLBACK_URI = "miketter://callback";
	private Twitter twitter = null;
	private RequestToken requestToken = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		twitter = new TwitterFactory().getInstance();
		twitter.setOAuthConsumer(getString(R.string.consumer_key),
				getString(R.string.consumer_secret));
		try {
			requestToken = twitter.getOAuthRequestToken(CALLBACK_URI);
		} catch (TwitterException e) {
			finish();
		}
		String authUrl = requestToken.getAuthorizationURL();
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
		startActivity(intent);
	}

	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Uri uri = intent.getData();
		if (uri != null && uri.toString().startsWith(CALLBACK_URI)) {
			String verifier = uri.getQueryParameter("oauth_verifier");
			if (verifier != null && verifier.length() > 0) {
				saveToken(verifier);
			}
		}
		finish();
	}

	private void saveToken(String oauthVerifier) {
		try {
			AccessToken accessToken = twitter.getOAuthAccessToken(requestToken,
					oauthVerifier);
			SharedPreferences pref = PreferenceManager
					.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString(getString(R.string.preference_key_token),
					accessToken.getToken());
			editor.putString(getString(R.string.preference_key_token_secret),
					accessToken.getTokenSecret());
			editor.commit();
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}
}
