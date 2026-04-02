#!/bin/bash
# build-demos.sh - 构建统一 Demo

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================"
echo "  SSM Rotation SDK Demo 构建脚本"
echo "========================================"

# 1. 编译主 SDK
echo ">>> 编译主 SDK..."
cd "$PROJECT_ROOT"
mvn clean install -DskipTests -q
echo "    ✓ 主 SDK 编译完成"

# 2. 编译统一 Spring Boot Demo
echo ">>> 编译统一 Spring Boot Demo..."
cd "$SCRIPT_DIR/springboot-demo"

mvn clean package -DskipTests -q
echo "    ✓ ssm-springboot-demo.jar"

# 汇总
echo ""
echo "========================================"
echo "  构建完成！"
echo "========================================"
echo ""
echo "生成的 jar 包："
ls -lh target/*.jar 2>/dev/null | grep -v original | awk '{print "  "$NF" ("$5")"}'
echo ""
echo "========================================"
echo "  使用示例"
echo "========================================"
echo ""
echo "# CAM 角色 + Hikari（推荐）"
echo "java -jar springboot-demo/target/ssm-springboot-demo.jar \\"
echo "  --demo.mode.auth=camrole --demo.mode.pool=hikari \\"
echo "  --ssm.cam-role-name=xxx --ssm.region=ap-guangzhou \\"
echo "  --ssm.secret-name=xxx --ssm.db.ip=xxx --ssm.db.port=3306 --ssm.db.name=xxx"
echo ""
echo "# 临时凭据 + JDBC"
echo "java -jar springboot-demo/target/ssm-springboot-demo.jar \\"
echo "  --demo.mode.auth=temporary --demo.mode.pool=jdbc \\"
echo "  --ssm.tmp-secret-id=xxx --ssm.tmp-secret-key=xxx --ssm.tmp-token=xxx \\"
echo "  --ssm.region=ap-guangzhou --ssm.secret-name=xxx --ssm.db.ip=xxx --ssm.db.port=3306 --ssm.db.name=xxx"
echo ""
echo "# 固定 AK/SK + Druid（不推荐，仅本地）"
echo "java -jar springboot-demo/target/ssm-springboot-demo.jar \\"
echo "  --demo.mode.auth=permanent --demo.mode.pool=druid \\"
echo "  --ssm.secret-id=xxx --ssm.secret-key=xxx --ssm.region=ap-guangzhou \\"
echo "  --ssm.secret-name=xxx --ssm.db.ip=xxx --ssm.db.port=3306 --ssm.db.name=xxx"
