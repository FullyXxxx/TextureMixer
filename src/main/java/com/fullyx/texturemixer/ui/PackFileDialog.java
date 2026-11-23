package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Explorer-style Windows Open dialog through the system DLL, independent of LWJGL tinyfd natives. */
public final class PackFileDialog {
    public static boolean windows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows");}
    public interface CommonDialog extends StdCallLibrary {
        boolean GetOpenFileNameW(OpenFileName value);
        int CommDlgExtendedError();
    }
    @Structure.FieldOrder({"lStructSize","hwndOwner","hInstance","lpstrFilter","lpstrCustomFilter","nMaxCustFilter","nFilterIndex","lpstrFile","nMaxFile","lpstrFileTitle","nMaxFileTitle","lpstrInitialDir","lpstrTitle","Flags","nFileOffset","nFileExtension","lpstrDefExt","lCustData","lpfnHook","lpTemplateName","pvReserved","dwReserved","FlagsEx"})
    public static final class OpenFileName extends Structure {
        public int lStructSize;
        public Pointer hwndOwner,hInstance,lpstrFilter,lpstrCustomFilter;
        public int nMaxCustFilter,nFilterIndex;
        public Pointer lpstrFile;
        public int nMaxFile;
        public Pointer lpstrFileTitle;
        public int nMaxFileTitle;
        public WString lpstrInitialDir,lpstrTitle;
        public int Flags;
        public short nFileOffset,nFileExtension;
        public WString lpstrDefExt;
        public Pointer lCustData,lpfnHook;
        public WString lpTemplateName;
        public Pointer pvReserved;
        public int dwReserved,FlagsEx;
    }
    public static String open(boolean main,long owner)throws IOException {
        String title=main?"TextureMixer - Main resource pack":"TextureMixer - Add resource pack";
        if(!windows())try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var patterns=stack.mallocPointer(1);patterns.put(0,stack.UTF8("*.zip"));
            return TinyFileDialogs.tinyfd_openFileDialog(title,null,patterns,"Minecraft Resource Pack (*.zip)",false);
        }
        var api=Native.load("comdlg32",CommonDialog.class);
        byte[] filter="Minecraft Resource Pack (*.zip)\0*.zip\0\0".getBytes(StandardCharsets.UTF_16LE);
        try(Memory file=new Memory(32768L*2);Memory filters=new Memory(filter.length)){
            file.clear();filters.write(0,filter,0,filter.length);
            var value=new OpenFileName();value.lStructSize=value.size();value.hwndOwner=owner==0?null:new Pointer(owner);
            value.lpstrFilter=filters;value.nFilterIndex=1;value.lpstrFile=file;value.nMaxFile=32768;
            value.lpstrTitle=new WString(title);value.lpstrDefExt=new WString("zip");
            // EXPLORER | FILEMUSTEXIST | PATHMUSTEXIST | NOCHANGEDIR | HIDEREADONLY | ENABLESIZING
            value.Flags=0x00080000|0x00001000|0x00000800|0x00000008|0x00000004|0x00800000;
            if(api.GetOpenFileNameW(value))return file.getWideString(0);
            int error=api.CommDlgExtendedError();if(error!=0)throw new IOException(tr("Windows 文件打开窗口失败 (")+Integer.toHexString(error)+")");
            return null;
        }
    }
}
