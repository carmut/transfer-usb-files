package com.example.appcloe;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 101;

    private UsbManager usbManager;
    StringBuilder usbInfo = new StringBuilder();
    String[] filesToCheck = {"Test"};

    String fileFrom = "Test";
    String fileTo = "Test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView usbInfoTextView = findViewById(R.id.usb_info_textview);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.android.example.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE);

        if (!hasPermissions()) {
            requestPermissions();
        }

        if (isConnected()) {
            usbInfo.append("Clé présente\n\n");
            for (String file : filesToCheck) {
                usbInfo.append("À chercher : ").append(file).append("\n");
            }

            if (checkFiles(filesToCheck)) {
                usbInfo.append("\t Fichier présent\n");
                boolean transfer = transferFiles(fileFrom, fileTo, "MODE.ini");
                if (transfer) {
                    usbInfo.append("\t\t Fichiers transférés avec succès\n");
                } else {
                    usbInfo.append("Échec du transfert des fichiers\n");
                }
            } else {
                usbInfo.append("\t Fichier non présent\n");
            }
        } else {
            usbInfo.append("Aucune clé USB présente\n");
        }



        usbInfoTextView.setText(usbInfo.toString());
    }

    private boolean isConnected() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        return !deviceList.isEmpty();
    }

    private boolean checkFiles(String[] filenames) {
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this);
        if (devices.length == 0) {
            usbInfo.append("Aucun périphérique de stockage de masse USB trouvé\n");
            return false;
        }

        UsbMassStorageDevice device = devices[0];

        try {
            if (!usbManager.hasPermission(device.getUsbDevice())) {
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.android.example.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE);
                usbManager.requestPermission(device.getUsbDevice(), permissionIntent);
                return false;
            }

            device.init();

            if (device.getPartitions().isEmpty()) {
                usbInfo.append("Aucune partition valide trouvée sur la clé USB\n");
                return false;
            }

            FileSystem fs = device.getPartitions().get(0).getFileSystem();
            UsbFile root = fs.getRootDirectory();
            UsbFile[] files = root.listFiles();

            for (String filename : filenames) {
                boolean fileFound = false;

                for (UsbFile file : files) {
                    if (file.getName().equals(filename)) {
                        fileFound = true;
                        usbInfo.append(file.getName()).append(" trouvé\n");
                        break;
                    }
                }

                if (!fileFound) {
                    usbInfo.append("Fichier ").append(filename).append(" non trouvé\n");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            usbInfo.append("Erreur lors de la vérification des fichiers : ").append(e.getMessage()).append("\n");
            return false;
        } finally {
            device.close();
        }
    }

    private boolean transferFiles(String locationDevice, String locationUSB, String fileExtension) {
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this);
        if (devices.length == 0) {
            usbInfo.append("Aucun périphérique de stockage de masse USB trouvé pour le transfert\n");
            return false;

        }

        UsbMassStorageDevice device = devices[0];

        try {
            if (!usbManager.hasPermission(device.getUsbDevice())) {
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.android.example.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE);
                usbManager.requestPermission(device.getUsbDevice(), permissionIntent);
                return false;
            }

            device.init();

            if (device.getPartitions().isEmpty()) {
                usbInfo.append("Aucune partition valide trouvée sur la clé USB\n");
                return false;
            }

            FileSystem fs = device.getPartitions().get(0).getFileSystem();
            UsbFile root = fs.getRootDirectory();

            UsbFile destinationFolder = root.search(locationUSB);
            if (destinationFolder == null) {
                destinationFolder = root.createDirectory(locationUSB);
            }

            File sourceFolder = new File(Environment.getExternalStorageDirectory(), locationDevice);
            if (sourceFolder.exists() && sourceFolder.isDirectory()) {
                copyDirectoryToUsb(sourceFolder, destinationFolder, fileExtension);
                return true;
            } else {
                usbInfo.append("Le dossier source n'existe pas ou n'est pas un dossier\n");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            usbInfo.append("Erreur lors du transfert des fichiers : ").append(e.getMessage()).append("\n");
            return false;
        } finally {
            device.close();
        }
    }


    private void copyDirectoryToUsb(File sourceDir, UsbFile destDir, String fileExtension) throws IOException {
        for (File file : sourceDir.listFiles()) {
            if (file.isDirectory()) {
                usbInfo.append("Création du dossier : ").append(file.getName()).append(" sur la clé USB\n");
                UsbFile newDir;
                try {
                    newDir = destDir.createDirectory(file.getName());
                } catch (IOException e) {
                    // Le dossier existe déjà, continue simplement
                    usbInfo.append("Le dossier ").append(file.getName()).append(" existe déjà, continuation...\n");
                    newDir = destDir.search(file.getName());
                    if (newDir == null) {
                        usbInfo.append("Erreur : Impossible de trouver le dossier existant ").append(file.getName()).append("\n");
                        continue;
                    }
                }
                // Appel récursif pour traiter les sous-dossiers
                copyDirectoryToUsb(file, newDir, fileExtension);
            } else {
                // Vérification de l'extension et copie du fichier
                if (shouldCopyFile(file, fileExtension)) {
                    usbInfo.append("Copie du fichier : ").append(file.getName()).append(" sur la clé USB\n");
                    copyFileToUsb(file, destDir);
                } else {
                    usbInfo.append("Fichier ignoré (extension non correspondante) : ").append(file.getName()).append("\n");
                }
            }
        }
    }

    private void copyFileToUsb(File file, UsbFile destDir) throws IOException {
        UsbFile usbFile;
        try {
            usbFile = destDir.createFile(file.getName());
        } catch (IOException e) {
            // Le fichier existe déjà, continuation
            usbInfo.append("Le fichier ").append(file.getName()).append(" existe déjà, remplacement...\n");
            usbFile = destDir.search(file.getName());
            if (usbFile == null) {
                usbInfo.append("Erreur : Impossible de trouver le fichier existant ").append(file.getName()).append("\n");
                return;
            }
        }

        try (FileInputStream fis = new FileInputStream(file);
             UsbFileOutputStream uos = new UsbFileOutputStream(usbFile)) {

            byte[] buffer = new byte[4096];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                uos.write(buffer, 0, length);
            }
        }
    }

    private boolean deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDirectory(child);
                }
            }
        }
        return file.delete();
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            int writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions are granted
            } else {
                // Permissions are denied
            }
        }
    }

    private boolean shouldCopyFile(File file, String filePattern) {
        String fileName = file.getName();
        String name = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";

        if (filePattern.equals("*.*")) {
            // Copier tous les fichiers
            return true;
        } else if (filePattern.startsWith("*.") && filePattern.equals("*" + extension)) {
            // Copier tous les fichiers ayant une certaine extension
            return true;
        } else if (filePattern.endsWith(".*") && filePattern.equals(name + ".*")) {
            // Copier tous les fichiers ayant un certain nom, quelle que soit l'extension
            return true;
        } else if (filePattern.equals(fileName)) {
            // Copier le fichier ayant exactement le même nom et la même extension
            return true;
        }

        // Aucune correspondance
        return false;
    }


}
