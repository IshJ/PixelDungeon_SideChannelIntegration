package com.watabou;//package com.watabou;
//
//import android.os.ParcelFileDescriptor;
//import android.os.RemoteException;
//
//import java.io.IOException;
//
//import com.example.sharedmemlib2.*;
//
//public class SharedMemImp extends ISharedMem.Stub{
//    @Override
//    public ParcelFileDescriptor OpenSharedMem(String name, int size, boolean create) throws RemoteException {
//        int fd =  ShmLib.OpenSharedMem(name,size,create);
//        try {
//            return ParcelFileDescriptor.fromFd(fd);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//}