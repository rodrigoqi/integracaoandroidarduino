/*
 * Copyright (C) 2019 Rodrigo Moreira Barreto
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.appintegracaoarduino;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

public class ConexaoArduino extends Thread{

    private BluetoothSocket blueSocket = null;
    private BluetoothServerSocket blueSocketServer = null;
    private InputStream inputDados = null;
    private OutputStream outputDados = null;
    private String blueMac = null;
    private String blueUUID = "00001101-0000-1000-8000-00805F9B34FB";
    private boolean isServer;
    private boolean isRunning = false;
    private boolean isConnected = false;

    Handler correio;

    //Construtor que prepara o dispositivo para atuar como servidor
    public ConexaoArduino(Handler correio) {
        this.correio = correio;
        this.isServer = true;
    }

    //Construtor que prepara o dispositivo para atuar como cliente passando por
    //parâmetro o MacAddress do dispositivo ao qual se deve solicitar a conexão
    public ConexaoArduino(String blueDevAddress, Handler correio) {
        this.correio = correio;
        this.isServer = false;
        this.blueMac = blueDevAddress;
    }

    //No run é implementado o que a Thread fará quando for disparada
    public void run() {
        this.correio = correio;
        //Informada que a Thread está em execução e pega uma instância
        //do adaptador bluetooth padrão do smartphone
        this.isRunning = true;
        BluetoothAdapter blueAdapter = BluetoothAdapter.getDefaultAdapter();

        //Determina o que a Thread deve fazer se foi instanciada como cliente ou servidor

        //Se for server (espera que um dispositivo cliente peça uma conexão)
        if(this.isServer) {
            try {
                //Cria um socket de servidor bluetooth usado para iniciar a conexão e fica
                //em estado de espera até que a conexão com cliente seja estabelecida
                //e quando isso ocorre (blueSocket!=null) o socket é liberado
                blueSocketServer = blueAdapter.listenUsingRfcommWithServiceRecord("PrjArduinoUUID", UUID.fromString(blueUUID));
                blueSocket = blueSocketServer.accept();
                if(blueSocket != null) {
                    blueSocketServer.close();
                }
            } catch (IOException e) {
                //caso uma exceção ocorra envia mensagem para a Activity informando
                e.printStackTrace();
                enviarParaActivity("#erroconexao".getBytes());
            }
        } else {

            //Se for cliente (envia um pedido de conexão a um dispositivo, no caso o Arduino)
            try {
                //Obtem uma representação do dispositivo Bluetooth com o endereço blueDevAddress
                BluetoothDevice blueDevice = blueAdapter.getRemoteDevice(blueMac);
                blueSocket = blueDevice.createRfcommSocketToServiceRecord(UUID.fromString(blueUUID));

                //cancela qualquer descoberta de dispositivos bluetooth em andamento
                blueAdapter.cancelDiscovery();

                //solicita uma conexão ao dispositivo com endereço blueDevAddress e espera
                //até que a conexão seja estabelecida
                if (blueSocket != null) {
                    blueSocket.connect();
                }

            } catch (IOException e) {
            //caso uma exceção ocorra envia mensagem para a Activity informando
                e.printStackTrace();
                enviarParaActivity("#erroconexao".getBytes());
            }

        }

        //se o blueSocket não é null é pq a conexão foi estabelecida com sucesso e retornada
        if(blueSocket != null) {
            //envia uma mensagem para a activity informando que a conexão ocorreu com sucesso
            this.isConnected = true;
            enviarParaActivity("#conectado".getBytes());

            try {
                //obtém um referência para os fluxos inputStream e o outputStream da conexão
                inputDados = blueSocket.getInputStream();
                outputDados = blueSocket.getOutputStream();

                //força uma espera cíclica até que uma mensagem seja recebida
                //e quando isso acontece envia a mesma (byte a byte) para a activity.
                //o laço é interrompido quando running recebe o valor false.
                while(isRunning) {
                    //estrutura para armazenar a mensagem recebida
                    //  bytes = número de bytes lidos na mensagem recebida
                    //  bytesRead = bytes lidos antes de achar uma quebra de linha
                    byte[] buffer = new byte[1024];
                    int bytes;
                    int bytesRead = -1;

                    do {
                        bytes = inputDados.read(buffer, bytesRead+1, 1);
                        bytesRead+=bytes;
                    } while(buffer[bytesRead] != '\n');

                    //quando a quebra é encontrada, envia os bytes guardados no buffer
                    //para a activity
                    enviarParaActivity(Arrays.copyOfRange(buffer, 0, bytesRead-1));
                }

            } catch (IOException e) {
                //caso ocorra uma exceção manda uma mensagem para a activity informando
                //problema na leitura dos dados
                e.printStackTrace();
                enviarParaActivity("#errodados".getBytes());
                this.isConnected = false;
            }
        }

    }

    //Método que envia o valor desejado para a activity através do seu Handler.
    //Pode ser tanto um valor recebido via bluetooth até uma mensagem nossa para informar
    //algo à activity (no caso, as com #)
    private void enviarParaActivity(byte[] dados) {
        //Cria um bundle, deposita o valor, anexa o mesmo no Message e envia para o handler
        Message mensagem = new Message();
        Bundle pacote = new Bundle();
        pacote.putByteArray("data", dados);
        mensagem.setData(pacote);
        correio.sendMessage(mensagem);
    }

    //Método que a activity pode chamar para que através do Stream de saída um valor
    //possa ser transmitido ao Arduino via Bluetooth
    public void enviarParaArduino(byte[] dados) {
        if(outputDados != null) {
            try {
                outputDados.write(dados);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            //Em caso de erro ao tentar transmitir é enviada uma mensagem à activity avisando
            enviarParaActivity("#errotransmissao".getBytes());
        }
    }

    //Método para encerrar a conexão e liberar os sockets. running recebe false
    //e isso encerra o laço em execução que fica capturando os valores recebidos
    //pela conexão bluetooth
    public void encerra() {
        try {
            isRunning = false;
            this.isConnected = false;
            blueSocketServer.close();
            blueSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        isRunning = false;
        this.isConnected = false;
    }

    //método que retorna se está conectado no momento
    public boolean isConnected() {
        return this.isConnected;
    }
}

