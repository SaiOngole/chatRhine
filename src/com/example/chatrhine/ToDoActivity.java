package com.example.chatrhine;

import static com.microsoft.windowsazure.mobileservices.MobileServiceQueryOperations.*;

import java.net.MalformedURLException;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.microsoft.windowsazure.messaging.NotificationHub;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.NextServiceFilterCallback;
import com.microsoft.windowsazure.mobileservices.ServiceFilter;
import com.microsoft.windowsazure.mobileservices.ServiceFilterRequest;
import com.microsoft.windowsazure.mobileservices.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.ServiceFilterResponseCallback;
import com.microsoft.windowsazure.mobileservices.TableOperationCallback;
import com.microsoft.windowsazure.mobileservices.TableQueryCallback;
import com.microsoft.windowsazure.mobileservices.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.MobileServiceAuthenticationProvider;
import com.microsoft.windowsazure.mobileservices.UserAuthenticationCallback;
import com.microsoft.windowsazure.notifications.NotificationsManager;
import com.microsoft.windowsazure.mobileservices.Registration;
import com.microsoft.windowsazure.mobileservices.RegistrationCallback;

public class ToDoActivity extends Activity {
	//	public static final String SENDER_ID = "axial-app-716";
	public static final String SENDER_ID = "477736743302";
	private GoogleCloudMessaging gcm;
	private NotificationHub hub;
	private String regID;
	/**
	 * Mobile Service Client reference
	 */
	private MobileServiceClient mClient;

	/**
	 * Mobile Service Table used to access data
	 */
	private MobileServiceTable<ToDoItem> mToDoTable;

	/**
	 * Adapter to sync the items list with the view
	 */
	private ToDoItemAdapter mAdapter;

	/**
	 * EditText containing the "New ToDo" text
	 */
	private EditText mTextNewToDo;

	/**
	 * Progress spinner to use for table operations
	 */
	private ProgressBar mProgressBar;

	/**
	 * Initializes the activity
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_to_do);

		mProgressBar = (ProgressBar) findViewById(R.id.loadingProgressBar);

		// Initialize the progress bar
		mProgressBar.setVisibility(ProgressBar.GONE);


		// Create the Mobile Service Client instance, using the provided
		// Mobile Service URL and key
		try {
			mClient = new MobileServiceClient(
					"https://is3av.azure-mobile.net/",
					"dEHAixEmtpsGFTFrRtLZVKfgqHlZEb37",
					this).withFilter(new ProgressFilter());
			NotificationsManager.handleNotifications(this, SENDER_ID, MyHandler.class);

			Log.d("auth", "done with auth");

			authenticate();
			Log.d("ToDoActivity",SENDER_ID);

		}
		catch(Exception e){
			e.printStackTrace();
		}
		gcm = GoogleCloudMessaging.getInstance(this);

		String connectionString = "Endpoint=sb://is3avhub-ns.servicebus.windows.net/;SharedAccessKeyName=DefaultListenSharedAccessSignature;SharedAccessKey=bAp1TpFO2wxPbcXv5yQHF5Iz8vCHwEZRlIOmynXXq2c=";
		hub = new NotificationHub("is3avhub-ns", connectionString, this);

		registerWithNotificationHubs();


	}
	@SuppressWarnings("unchecked")
	private void registerWithNotificationHubs() {
		new AsyncTask() {
			@Override
			protected Object doInBackground(Object... params) {
				try {
					String regid = gcm.register(SENDER_ID);
					hub.register(regid);
				} catch (Exception e) {
					return e;
				}
				return null;
			}
		}.execute(null, null, null);
	}
	/**
	 * Registers mobile services client to receive GCM push notifications
	 * @param gcmRegistrationId The Google Cloud Messaging session Id returned 
	 * by the call to GoogleCloudMessaging.register in NotificationsManager.handleNotifications
	 */
	public void registerForPush(String gcmRegistrationId)
	{
		mClient.getPush().register(gcmRegistrationId,null,new RegistrationCallback()
		{
			@Override
			public void onRegister(Registration registration, Exception exception)
			{
				if (exception != null)
				{
					// handle exception
					Log.d("errorstrng", "Did not register for push");
				}
			}
		});
	}   
	private void createTable() {
		// Get the Mobile Service Table instance to use
		mToDoTable = mClient.getTable(ToDoItem.class);

		mTextNewToDo = (EditText) findViewById(R.id.textNewToDo);

		// Create an adapter to bind the items with the view
		mAdapter = new ToDoItemAdapter(this, R.layout.row_list_to_do);
		ListView listViewToDo = (ListView) findViewById(R.id.listViewToDo);
		listViewToDo.setAdapter(mAdapter);

		// Load the items from the Mobile Service
		refreshItemsFromTable();

	}
	private void authenticate() {

		// Login using the Google provider.
		mClient.login(MobileServiceAuthenticationProvider.Twitter,
				new UserAuthenticationCallback() {

			@Override
			public void onCompleted(MobileServiceUser user,
					Exception exception, ServiceFilterResponse response) {
				Log.d("Test","Entered the login completed window");
				if (exception == null) {
					String temp = user.getUserId();
					regID = temp.substring(8, temp.length());
					Log.d("User ID",user.getUserId());
					Log.d("regID",regID);
					Log.d("Auth key",user.getAuthenticationToken());
					createAndShowDialog(String.format("You are logged in"),"Success");
					createTable();
				} else {
					createAndShowDialog("You must log in. Login Required", "Error");
				}
			}
		});
	}

	/**
	 * Initializes the activity menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	/**
	 * Select an option from the menu
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_refresh) {
			refreshItemsFromTable();
		}

		return true;
	}

	/**
	 * Mark an item as completed
	 * 
	 * @param item
	 *            The item to mark
	 */
	public void checkItem(ToDoItem item) {
		if (mClient == null) {
			return;
		}

		// Set the item as completed and update it in the table
		item.setComplete(true);

		mToDoTable.update(item, new TableOperationCallback<ToDoItem>() {

			public void onCompleted(ToDoItem entity, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					if (entity.isComplete()) {
						mAdapter.remove(entity);
					}
				} else {
					createAndShowDialog(exception, "Error");
				}
			}

		});
	}

	/**
	 * Add a new item
	 * 
	 * @param view
	 *            The view that originated the call
	 */
	public void addItem(View view) {
		if (mClient == null) {
			return;
		}

		// Create a new item
		ToDoItem item = new ToDoItem();

		item.setText(mTextNewToDo.getText().toString());
		item.setRegID(regID);
		item.setComplete(false);

		// Insert the new item
		mToDoTable.insert(item, new TableOperationCallback<ToDoItem>() {

			public void onCompleted(ToDoItem entity, Exception exception, ServiceFilterResponse response) {

				if (exception == null) {
					if (!entity.isComplete()) {
						mAdapter.add(entity);
					}
				} else {
					createAndShowDialog(exception, "Error");
				}

			}
		});

		mTextNewToDo.setText("");
	}

	/**
	 * Refresh the list with the items in the Mobile Service Table
	 */
	private void refreshItemsFromTable() {

		// Get the items that weren't marked as completed and add them in the
		// adapter

		mToDoTable.where().field("complete").eq(val(false)).execute(new TableQueryCallback<ToDoItem>() {

			public void onCompleted(List<ToDoItem> result, int count, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					mAdapter.clear();

					for (ToDoItem item : result) {
						mAdapter.add(item);
					}

				} else {
					createAndShowDialog(exception, "Error");
				}
			}
		});
	}

	/**
	 * Creates a dialog and shows it
	 * 
	 * @param exception
	 *            The exception to show in the dialog
	 * @param title
	 *            The dialog title
	 */
	private void createAndShowDialog(Exception exception, String title) {
		Throwable ex = exception;
		if(exception.getCause() != null){
			ex = exception.getCause();
		}
		createAndShowDialog(ex.getMessage(), title);
	}

	/**
	 * Creates a dialog and shows it
	 * 
	 * @param message
	 *            The dialog message
	 * @param title
	 *            The dialog title
	 */
	private void createAndShowDialog(String message, String title) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setMessage(message);
		builder.setTitle(title);
		if(!this.isFinishing()){
			builder.create().show();
		}
		
	}

	private class ProgressFilter implements ServiceFilter {

		@Override
		public void handleRequest(ServiceFilterRequest request, NextServiceFilterCallback nextServiceFilterCallback,
				final ServiceFilterResponseCallback responseCallback) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.VISIBLE);
				}
			});

			nextServiceFilterCallback.onNext(request, new ServiceFilterResponseCallback() {

				@Override
				public void onResponse(ServiceFilterResponse response, Exception exception) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.GONE);
						}
					});

					if (responseCallback != null)  responseCallback.onResponse(response, exception);
				}
			});
		}
	}
}
