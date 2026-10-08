#ifndef MINIZ_EXPORT_H
#define MINIZ_EXPORT_H

/* 静态链接构建使用的导出宏(等价于 CMake GenerateExportHeader 生成的 miniz_export.h) */
#ifndef MINIZ_EXPORT
#define MINIZ_EXPORT
#endif

#ifndef MINIZ_NO_EXPORT
#define MINIZ_NO_EXPORT
#endif

#endif /* MINIZ_EXPORT_H */