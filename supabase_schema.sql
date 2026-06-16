-- ============================================
-- LE Quote System — Supabase Database Schema
-- 在 Supabase SQL Editor 中运行本文件
-- ============================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS profiles (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'cn' CHECK (role IN ('cn','us','admin')),
  password TEXT NOT NULL DEFAULT 'qxyq01',
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','active','rejected')),
  approved_by TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
-- Seed admin
INSERT INTO profiles (id, name, role, password, status, approved_by)
VALUES ('U-ADMIN', 'qxyq01', 'admin', 'qxyq01', 'active', 'system')
ON CONFLICT (id) DO NOTHING;

-- 2. 报价表 (审批中心)
CREATE TABLE IF NOT EXISTS quotes (
  id TEXT PRIMARY KEY,
  hs TEXT NOT NULL,
  name TEXT NOT NULL,
  type TEXT DEFAULT 'tariff',
  approver TEXT,
  note TEXT,
  tag TEXT DEFAULT 'regular',
  company_name TEXT DEFAULT '',
  status TEXT DEFAULT 'pending_review',
  submitted_at TEXT,
  mfn REAL DEFAULT 0,
  s301 REAL DEFAULT 0,
  s122 REAL DEFAULT 0,
  le_rate REAL DEFAULT 0,
  le_duty REAL DEFAULT 0,
  broker_fee REAL DEFAULT 350,
  cargo_value REAL DEFAULT 0,
  total REAL DEFAULT 0,
  client TEXT DEFAULT '',
  reject_reason TEXT,
  counter_offer JSONB,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. 直接报价表
CREATE TABLE IF NOT EXISTS direct_requests (
  id TEXT PRIMARY KEY,
  hs TEXT NOT NULL,
  name TEXT NOT NULL,
  client TEXT NOT NULL,
  exw REAL DEFAULT 0,
  weight REAL DEFAULT 0,
  note TEXT,
  status TEXT DEFAULT 'pending',
  submitted_at TEXT,
  response JSONB,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. 公告板
CREATE TABLE IF NOT EXISTS bulletins (
  id TEXT PRIMARY KEY,
  content TEXT NOT NULL,
  author TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'cn',
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. 公告评论
CREATE TABLE IF NOT EXISTS comments (
  id BIGSERIAL PRIMARY KEY,
  post_id TEXT NOT NULL REFERENCES bulletins(id) ON DELETE CASCADE,
  author TEXT NOT NULL,
  text TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. 上传文件
CREATE TABLE IF NOT EXISTS files (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT DEFAULT 'OTHER',
  hs TEXT DEFAULT 'UNKNOWN',
  size BIGINT DEFAULT 0,
  data TEXT,
  uploaded_at TIMESTAMPTZ DEFAULT NOW(),
  uploaded_by TEXT DEFAULT 'anon'
);

-- 7. 订单管理 (v2.0+)
CREATE TABLE IF NOT EXISTS orders (
  id TEXT PRIMARY KEY,
  hs TEXT NOT NULL,
  name TEXT NOT NULL,
  company TEXT DEFAULT '',
  client TEXT DEFAULT '',
  total REAL DEFAULT 0,
  status TEXT DEFAULT 'processing',
  logistics JSONB DEFAULT '{}',
  source_id TEXT,
  confirmed_at TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. 客户流失记录
CREATE TABLE IF NOT EXISTS customer_lost (
  id TEXT PRIMARY KEY,
  hs TEXT NOT NULL,
  name TEXT NOT NULL,
  company TEXT DEFAULT '',
  reason TEXT DEFAULT '',
  recorded_at TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_quotes_status ON quotes(status);
CREATE INDEX IF NOT EXISTS idx_quotes_submitted ON quotes(submitted_at);
CREATE INDEX IF NOT EXISTS idx_direct_status ON direct_requests(status);
CREATE INDEX IF NOT EXISTS idx_bulletins_created ON bulletins(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_post ON comments(post_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_lost_created ON customer_lost(created_at DESC);

-- RLS 策略 (公开读取，公开写入 — 内网使用)
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE quotes ENABLE ROW LEVEL SECURITY;
ALTER TABLE direct_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE bulletins ENABLE ROW LEVEL SECURITY;
ALTER TABLE comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE files ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_lost ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all" ON profiles FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all" ON quotes FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all" ON direct_requests FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all" ON bulletins FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all" ON comments FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all" ON files FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all" ON orders FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all" ON customer_lost FOR ALL USING (true) WITH CHECK (true);
