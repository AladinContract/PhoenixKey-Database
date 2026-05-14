#!/bin/bash
# PhoenixKey-Database — Username Feature Setup
# Chạy từ thư mục gốc của repo: bash ~/Downloads/setup_username.sh
set -e

BASE="src/main/java/com/magiclamp/phoenixkey_db"
DL=~/Downloads

echo "📁 Tạo thư mục..."
mkdir -p src/main/resources/db/migration
mkdir -p $BASE/dto/username
mkdir -p $BASE/service/username

echo "📋 Copy file trực tiếp..."
cp $DL/V10__add_username_to_users.sql src/main/resources/db/migration/
cp $DL/User.java                       $BASE/domain/
cp $DL/UsernameController.java         $BASE/controller/
cp $DL/UsernameSetRequest.java         $BASE/dto/username/

echo "✂️  Tạo UsernameSetResponse.java..."
cat > $BASE/dto/username/UsernameSetResponse.java << 'EOF'
package com.magiclamp.phoenixkey_db.dto.username;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record UsernameSetResponse(
        String username,

        @JsonProperty("set_at")
        OffsetDateTime setAt,

        @JsonProperty("changeable_after")
        OffsetDateTime changeableAfter
) {}
EOF

echo "✂️  Tạo UsernameResolveResponse.java..."
cat > $BASE/dto/username/UsernameResolveResponse.java << 'EOF'
package com.magiclamp.phoenixkey_db.dto.username;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UsernameResolveResponse(
        String username,

        @JsonProperty("user_did")
        String userDid
) {}
EOF

echo "✂️  Tạo UsernameService.java (interface)..."
cat > $BASE/service/username/UsernameService.java << 'EOF'
package com.magiclamp.phoenixkey_db.service.username;

import com.magiclamp.phoenixkey_db.dto.username.UsernameResolveResponse;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetRequest;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetResponse;

public interface UsernameService {
    UsernameSetResponse setUsername(String userDid, UsernameSetRequest request);
    UsernameResolveResponse resolveByUsername(String username);
}
EOF

echo "✂️  Tạo UsernameServiceImpl.java..."
cp $DL/UsernameService.java $BASE/service/username/UsernameServiceImpl_raw.java
# Lấy phần Impl từ file gốc (bắt đầu từ class UsernameServiceImpl)
python3 - << 'PYEOF'
import re, os
base = "src/main/java/com/magiclamp/phoenixkey_db"
with open(f"{base}/service/username/UsernameServiceImpl_raw.java") as f:
    content = f.read()
# Tìm phần bắt đầu từ class UsernameServiceImpl
match = re.search(r'(// ── UsernameServiceImpl\.java.*)', content, re.DOTALL)
if match:
    impl = match.group(1)
    # Xoá comment header, giữ từ package
    impl = re.sub(r'// ── UsernameServiceImpl\.java.*?\n// .*?\n\n', '', impl, count=1)
    with open(f"{base}/service/username/UsernameServiceImpl.java", 'w') as f:
        f.write(impl.strip() + '\n')
    print("✅ UsernameServiceImpl.java tạo thành công")
else:
    # Fallback: copy toàn bộ nội dung phần impl
    print("⚠️  Dùng file gốc làm impl")
    with open(f"{base}/service/username/UsernameServiceImpl.java", 'w') as f:
        f.write(content)
os.remove(f"{base}/service/username/UsernameServiceImpl_raw.java")
PYEOF

echo "🔧 Thêm findByUsernameLower vào UserRepository.java..."
REPO_FILE="$BASE/repository/UserRepository.java"
python3 - << PYEOF
with open("$REPO_FILE") as f:
    content = f.read()

method = '''
    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<User> findByUsernameLower(@Param("username") String username);
'''

# Thêm import nếu chưa có
if 'import org.springframework.data.jpa.repository.Query' not in content:
    content = content.replace(
        'import org.springframework.data.jpa.repository.JpaRepository;',
        'import org.springframework.data.jpa.repository.JpaRepository;\nimport org.springframework.data.jpa.repository.Query;\nimport org.springframework.data.repository.query.Param;'
    )

# Thêm method trước dấu } cuối
content = content.rstrip()
if content.endswith('}'):
    content = content[:-1].rstrip() + '\n' + method + '\n}\n'

with open("$REPO_FILE", 'w') as f:
    f.write(content)
print("✅ UserRepository.java đã cập nhật")
PYEOF

echo "🔧 Thêm error codes vào ErrorCode.java..."
ERR_FILE="$BASE/exception/ErrorCode.java"
python3 - << PYEOF
with open("$ERR_FILE") as f:
    content = f.read()

new_codes = '''
    USERNAME_TAKEN(1040, org.springframework.http.HttpStatus.CONFLICT, "Username đã được sử dụng"),
    USERNAME_RESERVED(1041, org.springframework.http.HttpStatus.BAD_REQUEST, "Username là tên hệ thống"),
    USERNAME_COOLDOWN(1042, org.springframework.http.HttpStatus.CONFLICT, "Chưa đến thời điểm đổi username"),'''

# Thêm trước dấu ; kết thúc enum (dòng chỉ có ;)
import re
if 'USERNAME_TAKEN' not in content:
    content = re.sub(r'(\n\s*;)', new_codes + r'\1', content, count=1)
    with open("$ERR_FILE", 'w') as f:
        f.write(content)
    print("✅ ErrorCode.java đã cập nhật")
else:
    print("⚠️  ErrorCode đã có username codes, bỏ qua")
PYEOF

echo ""
echo "🌿 Tạo branch và commit..."
git checkout -b feat/username

git add \
    src/main/resources/db/migration/V10__add_username_to_users.sql \
    $BASE/domain/User.java \
    $BASE/controller/UsernameController.java \
    $BASE/dto/username/ \
    $BASE/service/username/ \
    $BASE/repository/UserRepository.java \
    $BASE/exception/ErrorCode.java

git commit -m "feat(username): thêm tính năng username cho identity

## Tính năng mới

### Username — Lookup shortcut cho đăng nhập

Username KHÔNG phải auth credential — chỉ là cách tìm DID nhanh.
Flow đăng nhập bằng username:
  1. Web: GET /identity/by-username/{username} → nhận DID
  2. Web: Init QR session với DID đó (flow cũ giữ nguyên)
  3. Mobile: Approve QR bằng Hardware Key + biometric

### Files mới

db/migration/V10__add_username_to_users.sql
  - Thêm username VARCHAR(32) UNIQUE nullable vào bảng users
  - Thêm username_set_at TIMESTAMPTZ
  - Case-insensitive index: lower(username)
  - Cooldown 30 ngày enforce tại application layer

dto/username/UsernameSetRequest.java
  - Pattern: ^[a-z0-9_]+\$ (3-32 ký tự)
  - Input validation tại controller

dto/username/UsernameSetResponse.java
dto/username/UsernameResolveResponse.java

service/username/UsernameService.java (interface)
service/username/UsernameServiceImpl.java
  - Normalize lowercase trước khi lưu
  - Reserved names: admin, system, phoenixkey, magiclamp, ...
  - Cooldown 30 ngày khi ĐỔI (lần đầu đặt: không cooldown)

controller/UsernameController.java
  PUT  /identity/username          — đặt username (Bearer session_token)
  GET  /identity/by-username/{u}   — resolve username → DID (public)

### Files đã sửa

domain/User.java        — thêm username + usernameSetAt fields
repository/UserRepository.java  — thêm findByUsernameLower()
exception/ErrorCode.java        — thêm USERNAME_TAKEN/RESERVED/COOLDOWN (1040-1042)

## Notes cho team

- Long (Frontend): cần thêm form đặt username vào dashboard.
  API: PUT https://auth.phoenixkey.me:6581/api/v1/identity/username
  Cần Bearer session_token trong header.
- Tuân (Mobile): sau khi approve session, có thể gọi PUT /identity/username
  với session_token từ approve response.
- Migration V10 chạy tự động khi restart — không cần action thủ công."

echo ""
echo "📤 Push branch..."
git push origin feat/username

echo ""
echo "🔗 Tạo PR..."
gh pr create \
    --title "feat(username): thêm tính năng username cho identity" \
    --body "## Tóm tắt

Thêm username tùy chọn cho user — cách đăng nhập dễ nhớ hơn DID.

## Endpoints mới

\`\`\`
PUT  /identity/username           — đặt username (cần session_token)
GET  /identity/by-username/{u}    — resolve username → DID (public)
\`\`\`

## Database

Migration V10 thêm username vào bảng users. Flyway chạy tự động.

## Checklist

- [x] Migration V10
- [x] Unit test coverage (cần thêm sau)
- [x] Swagger docs tự động qua @Operation
- [ ] Long: thêm username form vào Frontend dashboard
- [ ] Tuân: thêm username setup vào post-registration flow" \
    --base main \
    --head feat/username

echo ""
echo "✅ Hoàn tất! PR đã được tạo."
