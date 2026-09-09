package com.jeerovan.fixtures;
public class FixtureActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Notification fixture opened"); setContentView(text);
    }
}
