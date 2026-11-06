# Keeps the line numbers of the stack traces reported by Play Console and Crashlytics, with the
# mapping file naming what R8 renamed. The source file's name is of no use once classes are renamed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
