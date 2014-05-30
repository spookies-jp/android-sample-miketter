package jp.co.spookies.android.miketter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MiketterActivity extends Activity {
	private final int REQUEST_CODE_MIC = 502;
	private final int MIC_CAND_SIZE = 5;
	private Twitter twitter = new TwitterFactory().getInstance();
	private Handler handler = new Handler();
	private String message = "";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);
		twitter.setOAuthConsumer(getString(R.string.consumer_key),
				getString(R.string.consumer_secret));
	}

	public void onReloadButtonClicked(View view) {
		try {
			reload();
		} catch (TwitterException e) {
			showErrorDialog();
		}
	}

	public void onMicButtonClicked(View arg0) {
		startMic();
	}

	private void startMic() {
		try {
			Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
					RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
					R.string.title_mic_message);
			startActivityForResult(intent, REQUEST_CODE_MIC);
		} catch (ActivityNotFoundException e) {
			showErrorDialog();
		}

	}

	/**
	 * 更新 タイムラインを再読み込み
	 * 
	 * @throws TwitterException
	 */
	private void reload() throws TwitterException {
		setProgressBarIndeterminateVisibility(true);
		Thread thread = new Thread() {
			@Override
			public void run() {
				try {
					final List<Status> statuses = twitter
							.getHomeTimeline(new Paging(1, 20));
					handler.post(new Runnable() {
						@Override
						public void run() {
							ListView listView = (ListView) findViewById(R.id.time_line);
							TwitterAdapter adapter = new TwitterAdapter(
									MiketterActivity.this, R.layout.tweet_row,
									statuses);
							listView.setAdapter(adapter);
							setProgressBarIndeterminateVisibility(false);
						}
					});
				} catch (TwitterException e) {
					e.printStackTrace();
					handler.post(new Runnable() {
						@Override
						public void run() {
							showErrorDialog();
							setProgressBarIndeterminateVisibility(false);
						}
					});
				}
			}
		};
		thread.run();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		// MIC入力後
		if (requestCode == REQUEST_CODE_MIC) {
			if (resultCode == RESULT_OK) {
				List<CharSequence> results = intent
						.getCharSequenceArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				if (results.size() > MIC_CAND_SIZE) {
					results = results.subList(0, MIC_CAND_SIZE);
				}
				results.add("");
				AlertDialog.Builder dialog = new AlertDialog.Builder(this);
				dialog.setTitle(R.string.select_dialog_title);
				final CharSequence[] items = results
						.toArray(new CharSequence[0]);
				dialog.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						message += items[which];
						createTweetDialog().show();
					}
				});
				dialog.setOnCancelListener(new OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						message = "";
					}
				});
				dialog.create().show();
			} else {
				message = "";
			}
		}
	}

	private AlertDialog createTweetDialog() {
		AlertDialog.Builder tweetDialog = new AlertDialog.Builder(
				MiketterActivity.this);
		tweetDialog.setTitle(R.string.tweet_dialog_title);
		tweetDialog.setMessage(message);
		tweetDialog.setPositiveButton(R.string.tweet_button_text,
				new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						tweet(message);
					}
				});
		tweetDialog.setNeutralButton(R.string.next_button_text,
				new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startMic();
					}
				});
		tweetDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				message = "";
			}
		});
		return tweetDialog.create();
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(this);
		String token = pref.getString(getString(R.string.preference_key_token),
				"");
		String tokenSecret = pref.getString(
				getString(R.string.preference_key_token_secret), "");
		// AccesToken情報がプリファレンスに登録されているか
		if (StringUtils.isNotEmpty(token)
				&& StringUtils.isNotEmpty(tokenSecret)) {
			// ある場合は，メイン画面を表示
			twitter.setOAuthAccessToken(new AccessToken(token, tokenSecret));
			try {
				reload();
			} catch (TwitterException e) {
				showErrorDialog();
			}
		} else {
			// ない場合は，認証画面へ
			startLoginActivity();
		}
	}

	private void startLoginActivity() {
		Intent intent = new Intent(this, LoginActivity.class);
		this.startActivity(intent);
	}

	private void showErrorDialog() {
		Toast.makeText(this, getText(R.string.error), Toast.LENGTH_LONG).show();
	}

	private void tweet(String message) {
		try {
			if (StringUtils.isNotEmpty(message)) {
				twitter.updateStatus(message);
			}
			message = "";
			reload();
		} catch (TwitterException e) {
			e.printStackTrace();
			showErrorDialog();
		}
	}

	class TwitterAdapter extends ArrayAdapter<Status> {
		private List<Status> tweets = null;
		private LayoutInflater inflater = null;
		private Map<String, Drawable> images = new HashMap<String, Drawable>();

		public TwitterAdapter(Context context, int textViwResourceId,
				List<Status> items) {
			super(context, textViwResourceId, items);
			this.tweets = items;
			this.inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null) {
				view = inflater.inflate(R.layout.tweet_row, null);
			}

			Status tweet = tweets.get(position);

			if (tweet != null) {
                // アイコンのセット
				ImageView imageIcon = (ImageView) view.findViewById(R.id.icon);
				imageIcon.setImageDrawable(getImage(tweet));
                // 名前のセット
				TextView textName = (TextView) view.findViewById(R.id.name);
				textName.setText(tweet.getUser().getName());
                // tweetのセット
				TextView textTweet = (TextView) view.findViewById(R.id.tweet);
				textTweet.setText(tweet.getText());
                // 時刻のセット
				TextView textTime = (TextView) view.findViewById(R.id.time);
				Date createdAt = tweet.getCreatedAt();
				String date = DateFormat.getDateFormat(MiketterActivity.this)
						.format(createdAt);
				String time = DateFormat.getTimeFormat(MiketterActivity.this)
						.format(createdAt);
				textTime.setText(date + ' ' + time);
			}
			return view;
		}

		private Drawable getImage(Status tweet) {
			String userId = tweet.getUser().getScreenName();
			// メモリに読まれている
			if (images.containsKey(userId)) {
				return images.get(userId);
			}
			// ファイルキャッシュにある
			try {
				InputStream in = openFileInput(userId);
				Drawable d = Drawable.createFromStream(in, null);
				images.put(userId, d);
				return d;
			} catch (IOException e) {
			}
			// Twitterから取得
			try {
				HttpGet httpRequest = new HttpGet(tweet.getUser()
						.getProfileImageURL().toString());
				HttpClient httpClient = new DefaultHttpClient();
				HttpResponse response = (HttpResponse) httpClient
						.execute(httpRequest);
				BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(
						response.getEntity());
				InputStream in = bufHttpEntity.getContent();
				byte[] image = new byte[in.available()];
				in.read(image);
				OutputStream out = openFileOutput(userId, 0);
				out.write(image);
				in = openFileInput(userId);
				Drawable d = Drawable.createFromStream(in, null);
				images.put(userId, d);
				return d;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
}
