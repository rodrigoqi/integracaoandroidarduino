package com.example.appintegracaoarduino;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    Button btnLigarDesligarPorta;
    TextView txtDados, txtStatus;
    private ConexaoArduino conexao;
    public Correio correio = new Correio();
    private boolean portaOn = false;
    ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

    MediaPlayer mp;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        txtDados = findViewById(R.id.txtDados);
        txtStatus = findViewById(R.id.txtStatus);
        btnLigarDesligarPorta = findViewById(R.id.btnLigarDesligarPorta);

        btnLigarDesligarPorta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ligarDesligarPorta();
            }
        });

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        btAdapter.enable();

        conexao = new ConexaoArduino("98:D3:21:FC:83:E7", correio);
        conexao.start();

        try {
            Thread.sleep(1000);
        } catch (Exception E) {
            E.printStackTrace();
        }


    }

    public class Correio extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            byte[] data = bundle.getByteArray("data");
            String dataString = new String(data);

            if (dataString.equals("#erroconexao")){
                txtStatus.setText("Não foi possível conectar");
            } else if (dataString.equals("#errotransmissao")){
                txtStatus.setText("Não foi possível realizar a transmissão");
            } else if (dataString.equals("#errodados")){
                txtStatus.setText("Não foi possível ler os dados");
            } else if (dataString.equals("#conectado")){
                txtStatus.setText("Conectado com sucesso ao Arduino");
            } else {
                float distancia = Float.parseFloat(dataString);
                if(distancia<=15){
                    tone.startTone(ToneGenerator.TONE_CDMA_PIP,50);
                }
                txtDados.setText(dataString);
            }
        }
    }

    private void ligarDesligarPorta(){
        if(portaOn){
            btnLigarDesligarPorta.setCompoundDrawablesWithIntrinsicBounds(R.drawable.on, 0, 0, 0);
            btnLigarDesligarPorta.setText("Ligar Porta");
            conexao.enviarParaArduino("off".getBytes());
        } else {
            btnLigarDesligarPorta.setCompoundDrawablesWithIntrinsicBounds(R.drawable.off, 0, 0, 0);
            btnLigarDesligarPorta.setText("Desligar Porta");
            conexao.enviarParaArduino("on".getBytes());
        }
        portaOn = !portaOn;
    }


}

