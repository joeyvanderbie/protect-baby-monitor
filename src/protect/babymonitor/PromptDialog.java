package protect.babymonitor;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class PromptDialog extends DialogFragment implements OnEditorActionListener {
	PromptDialogListener listener ;
	
	  public interface PromptDialogListener {
	        void onFinishPromptDialog(String inputText);
	    }
	
	    private EditText passwordText;

	    public void setPromptDialogListener(PromptDialogListener listener) {
	        this.listener = listener;
	    }
	  
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.prompt, container, false);
        getDialog().setTitle("Simple Dialog 2");
        
        passwordText = (EditText) rootView.findViewById(R.id.password);
        passwordText.setOnEditorActionListener(this);
        
        Button dismiss = (Button) rootView.findViewById(R.id.dismiss);
        dismiss.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                listener.onFinishPromptDialog(passwordText.getText().toString());
                  dismiss();
            }
        });
        return rootView;
    }

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		if (EditorInfo.IME_ACTION_DONE == actionId) {

            // Return input text to activity

            listener.onFinishPromptDialog(passwordText.getText().toString());
            dismiss();

            return true;

        }

        return false;
	}
}