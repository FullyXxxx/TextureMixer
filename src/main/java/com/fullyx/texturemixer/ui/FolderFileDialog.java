package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.util.UUID;

/** Windows IFileOpenDialog with FOS_PICKFOLDERS, owned and pumped by the game's UI thread. */
public final class FolderFileDialog {
    private static final int CANCELLED=0x800704c7;
    private interface Ole32 extends StdCallLibrary {
        int CoInitializeEx(Pointer reserved,int flags);
        int CoCreateInstance(Guid clsid,Pointer outer,int context,Guid iid,PointerByReference result);
        void CoTaskMemFree(Pointer memory);
        void CoUninitialize();
    }
    @Structure.FieldOrder({"data1","data2","data3","data4"})
    public static final class Guid extends Structure {
        public int data1;
        public short data2,data3;
        public byte[] data4=new byte[8];
        public Guid(String value){
            UUID id=UUID.fromString(value);
            long high=id.getMostSignificantBits(),low=id.getLeastSignificantBits();
            data1=(int)(high>>>32);data2=(short)(high>>>16);data3=(short)high;
            for(int i=0;i<8;i++)data4[i]=(byte)(low>>>(56-i*8));
        }
    }
    private static int invoke(Pointer object,int slot,Object... arguments){
        Pointer address=object.getPointer(0).getPointer((long)slot*Native.POINTER_SIZE);
        Object[] nativeArguments=new Object[arguments.length+1];nativeArguments[0]=object;
        System.arraycopy(arguments,0,nativeArguments,1,arguments.length);
        return Function.getFunction(address,Function.ALT_CONVENTION).invokeInt(nativeArguments);
    }
    private static void requireSuccess(int result)throws IOException {
        if(result<0)throw new IOException(tr("Windows 文件夹选择失败 (")+Integer.toHexString(result)+")");
    }
    public static String open(long owner)throws IOException {
        if(!PackFileDialog.windows())return org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog("TextureMixer - Add folder",(String)null);
        Ole32 api=Native.load("ole32",Ole32.class);
        int initialized=api.CoInitializeEx(null,6);
        Pointer dialog=null,item=null,path=null;
        try {
            var result=new PointerByReference();
            requireSuccess(api.CoCreateInstance(new Guid("dc1c5a9c-e88a-4dde-a5a1-60f82a20aef7"),null,1,
                    new Guid("d57c7288-d4ad-4768-be02-9d969532d960"),result));
            dialog=result.getValue();
            requireSuccess(invoke(dialog,9,0x20|0x40|0x800|0x8));
            requireSuccess(invoke(dialog,17,new WString("TextureMixer - Add resource pack folder")));
            int shown=invoke(dialog,3,owner==0?null:new Pointer(owner));
            if(shown==CANCELLED)return null;
            requireSuccess(shown);
            var selected=new PointerByReference();requireSuccess(invoke(dialog,20,selected));item=selected.getValue();
            var displayName=new PointerByReference();requireSuccess(invoke(item,5,0x80058000,displayName));
            path=displayName.getValue();return path.getWideString(0);
        }finally {
            if(path!=null)api.CoTaskMemFree(path);
            if(item!=null)invoke(item,2);
            if(dialog!=null)invoke(dialog,2);
            if(initialized>=0)api.CoUninitialize();
        }
    }
}
