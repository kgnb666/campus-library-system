-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V9: 毕业答辩与生产演示全真种子数据 (Stage 7-A)
-- 包含：标准答辩演示三角色、五大门类 52 本图文经典书目、物理单册、借阅/预约流水、通知与 AI 导读
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 确保核心图书分类体系完备 (CS, LIT, ECON, SCI, HIST, PHIL)
-- ----------------------------------------------------------------------
INSERT INTO categories (code, name, description, sort_order, status) VALUES
('HIST', '历史与地理科学', '中国古代史、近现代史、世界通史、考古与人文地理', 6, 'ACTIVE')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description;

-- ----------------------------------------------------------------------
-- 2. 预置毕业答辩三大核心演示用户 (密码均为 123456, BCrypt Cost=12)
-- Hash: $2a$12$S0xUBtgZ0p1DfmS8n87fi.fAFqKiTC3F9fZ7uWGZnYjcvMywhpN3q
-- ----------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, nickname, status, created_at, updated_at) VALUES
('student_demo', 'student_demo@campus.edu.cn', '$2a$12$S0xUBtgZ0p1DfmS8n87fi.fAFqKiTC3F9fZ7uWGZnYjcvMywhpN3q', '演示学生 (张三)', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('librarian_demo', 'librarian_demo@campus.edu.cn', '$2a$12$S0xUBtgZ0p1DfmS8n87fi.fAFqKiTC3F9fZ7uWGZnYjcvMywhpN3q', '演示馆员 (王老师)', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('admin_demo', 'admin_demo@campus.edu.cn', '$2a$12$S0xUBtgZ0p1DfmS8n87fi.fAFqKiTC3F9fZ7uWGZnYjcvMywhpN3q', '演示系统管理员', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

-- 绑定演示用户借阅规则
UPDATE users u
SET borrow_rule_id = r.id
FROM borrowing_rules r
WHERE u.username = 'student_demo' AND r.user_type = 'STUDENT';

UPDATE users u
SET borrow_rule_id = r.id
FROM borrowing_rules r
WHERE u.username = 'librarian_demo' AND r.user_type = 'LIBRARIAN';

UPDATE users u
SET borrow_rule_id = r.id
FROM borrowing_rules r
WHERE u.username = 'admin_demo' AND r.user_type = 'ADMIN';

-- 绑定演示用户角色
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'student_demo' AND r.code = 'STUDENT'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'librarian_demo' AND r.code = 'LIBRARIAN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin_demo' AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------
-- 3. 预置五大分类 52 本经典馆藏书目 (涵盖计算机、文学、经管、数理自然、历史)
-- ----------------------------------------------------------------------

-- 3.1 计算机科学与技术 (12本)
INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111544937', '深入理解计算机系统 (原书第3版)', '程序员的必读硬核圣经', 'Randal E. Bryant', '机械工业出版社', '2016-11-01', c.id,
       '从程序员视角全面剖析计算机系统底层逻辑，涵盖汇编语言、处理器体系结构、存储层次、链接与虚拟内存、并发编程。',
       'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400', 3, 0, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111407010', '算法导论 (原书第3版)', '算法与数据结构经典著作', 'Thomas H. Cormen', '机械工业出版社', '2013-01-01', c.id,
       '国内外高校广泛采用的权威算法经典教材，深入浅出解析排序、动态规划、贪心算法、图论与NP完全性。',
       'https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111075752', '设计模式：可复用面向对象软件的基础', 'GoF经典设计模式精髓', 'Erich Gamma 等', '机械工业出版社', '2000-09-01', c.id,
       '软件工程经典名著，系统阐述单例、工厂、观察者、适配器等23种经典面向对象设计模式及设计原则。',
       'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787115216878', '代码整洁之道', 'Clean Code 程序员专业修炼', 'Robert C. Martin', '人民邮电出版社', '2010-01-01', c.id,
       '讲解编写整洁代码的最佳实践、函数设计、命名规范、单元测试原则与并发重构方法。',
       'https://images.unsplash.com/photo-1504639725590-34d0984388bd?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787115508645', '重构：改善既有代码的设计 (第2版)', '重构领域的奠基经典', 'Martin Fowler', '人民邮电出版社', '2019-04-01', c.id,
       '系统介绍代码坏味道识别、常用重构手法及自动化单元测试在既有系统架构治理中的实践。',
       'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111641247', 'Java核心技术 卷I：基础知识', 'Core Java 全面指南', 'Cay S. Horstmann', '机械工业出版社', '2020-03-01', c.id,
       '全面覆盖面向对象、接口与Lambda表达式、泛型机制、异常捕获以及并发基础编程。',
       'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787115531407', 'Spring Boot实战 (第4版)', 'Spring企业级敏捷开发指南', 'Craig Walls', '人民邮电出版社', '2020-05-01', c.id,
       '手把手指导使用 Spring Boot 构建生产就绪应用、自动配置机制与微服务实战。',
       'https://images.unsplash.com/photo-1607799279861-4dd421887fb3?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787121443657', '高性能MySQL (第4版)', '大规模数据库架构与性能调优', 'Silvia Botros', '电子工业出版社', '2022-11-01', c.id,
       '深挖关系型数据库底层引擎、索引优化、锁与事务隔离、高可用复制架构设计。',
       'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111464747', 'Redis设计与实现', '探究高并发内存缓存底层', '黄健宏', '机械工业出版社', '2014-06-01', c.id,
       '系统解析 Redis 字符串、哈希、跳跃表等底层数据结构与持久化 AOF/RDB 机制。',
       'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111612728', '操作系统概念 (原书第9版)', '经典恐龙书理论基石', 'Abraham Silberschatz', '机械工业出版社', '2018-09-01', c.id,
       '权威操作系统教材，详述进程调度、线程死锁、虚存分页管理与文件系统保护。',
       'https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=400', 2, 0, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111599715', '计算机网络：自顶向下方法 (原书第7版)', '现代计算机网络核心', 'James F. Kurose', '机械工业出版社', '2018-06-01', c.id,
       '自顶向下由应用层逐步推进至传输层TCP/UDP、网络层IP路由与链路层协议。',
       'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787115461475', '深度学习', '人工智能花书奠基之作', 'Ian Goodfellow 等', '人民邮电出版社', '2017-08-01', c.id,
       '全球公认的人工智能深度学习圣经，涵盖卷积网络、循环神经网络与生成对抗模型。',
       'https://images.unsplash.com/photo-1504639725590-34d0984388bd?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'CS' ON CONFLICT (isbn) DO NOTHING;

-- 3.2 文学与艺术 (10本)
INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787020002207', '红楼梦', '中国古典章回小说巅峰之作', '曹雪芹', '人民文学出版社', '1982-02-01', c.id,
       '以贾、史、王、薛四大家族为背景，展现封建大家族盛衰与青年儿女悲欢离合。',
       'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=400', 5, 4, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787544253994', '百年孤独', '魔幻现实主义文学殿堂巨作', '加西亚·马尔克斯', '南海出版公司', '2011-06-01', c.id,
       '布恩迪亚家族七代人的传奇故事，融合现实与神话，展现拉美历史的孤独宿命。',
       'https://images.unsplash.com/photo-1476275466078-4007374efbbe?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787536692909', '三体全集', '雨果奖获奖科幻里程碑', '刘慈欣', '重庆出版社', '2008-01-01', c.id,
       '讲述地球人类与三体文明跨越数百年的生死博弈，展现宇宙黑暗森林法则。',
       'https://images.unsplash.com/photo-1532012164546-f432f2e3edd4?w=400', 5, 4, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787020083985', '围城', '当代学者世态人情长篇小说', '钱钟书', '人民文学出版社', '1991-02-01', c.id,
       '围在城里的人想逃出来，城外的人想冲进去，婚姻也罢，职业也罢，人生的隐喻。',
       'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787530216781', '平凡的世界 (全三册)', '茅盾文学奖经典现实主义', '路遥', '北京十月文艺出版社', '2017-06-01', c.id,
       '以孙少安、孙少平兄弟两人的奋斗经历为主线，刻画普通人在时代洪流中的坚韧与崇高。',
       'https://images.unsplash.com/photo-1476275466078-4007374efbbe?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787506365437', '活着', '生命历程与历史苦难的深沉见证', '余华', '作家出版社', '2012-08-01', c.id,
       '地主少爷福贵一生坎坷经历，生动诠释人是为了活着本身而活着的坚韧力量。',
       'https://images.unsplash.com/photo-1532012164546-f432f2e3edd4?w=400', 4, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787544258609', '白夜行', '东野圭吾悬疑推理巅峰代表作', '东野圭吾', '南海出版公司', '2013-01-01', c.id,
       '只希望能手牵手在太阳下散步，讲述绝望与羁绊交织的十九年人性悲歌。',
       'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787208061644', '追风筝的人', '为你千千万万遍的关于救赎的温情史诗', '卡勒德·胡赛尼', '上海人民出版社', '2006-05-01', c.id,
       '以阿富汗喀布尔为背景，讲述关于友情、背叛、救赎与人性的深情成长故事。',
       'https://images.unsplash.com/photo-1476275466078-4007374efbbe?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787532771691', '月亮与六便士', '理想与世俗的灵魂碰撞', '威廉·萨默塞特·毛姆', '上海译文出版社', '2016-09-01', c.id,
       '满地都是六便士，他却抬头看见了月亮。以高更为原型探讨艺术激情与世俗生活的抉择。',
       'https://images.unsplash.com/photo-1532012164546-f432f2e3edd4?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787532725694', '老人与海', '人可以被毁灭，但不能给打败', '欧内斯特·海明威', '上海译文出版社', '2001-01-01', c.id,
       '老渔夫圣地亚哥与巨大马林鱼搏斗的硬汉寓言，诺贝尔文学奖获奖传世名作。',
       'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'LIT' ON CONFLICT (isbn) DO NOTHING;

-- 3.3 经济与管理科学 (10本)
INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787301258660', '经济学原理 (微观+宏观)', '哈佛大学权威经济学入门经典', 'N.格里高利·曼昆', '北京大学出版社', '2015-05-01', c.id,
       '以极其生动幽默的语言系统介绍十大经济学原理、供求弹性、消费者剩余与宏观货币政策。',
       'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787100018159', '国富论 (全二册)', '现代经济学的奠基巨著', '亚当·斯密', '商务印书馆', '1972-12-01', c.id,
       '提出看不见的手与劳动分工理论，全面奠定古典政治经济学思想基石。',
       'https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787508633558', '思考，快与慢', '行为经济学与认知心理学开山经典', '丹尼尔·卡尼曼', '中信出版社', '2012-07-01', c.id,
       '剖析大脑快思考系统1与慢思考系统2，揭示人类决策中的认知偏差与启发式陷阱。',
       'https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787508640709', '贫穷的本质', '我们为什么摆脱不掉贫穷', '阿比吉特·班纳吉 等', '中信出版社', '2013-04-01', c.id,
       '诺贝尔经济学奖得主通过 15 年实地田野调查，揭开穷人陷入贫困陷阱的核心逻辑。',
       'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787100057936', '资本论 (第一卷)', '马克思政治经济学鸿篇巨制', '卡尔·马克思', '商务印书馆', '2004-04-01', c.id,
       '剖析商品、货币、剩余价值产生规律，深刻揭示资本主义生产方式的内在矛盾。',
       'https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787115421677', '聪明的投资者 (第4版)', '价值投资圣经巴菲特力荐', '本杰明·格雷厄姆', '人民邮电出版社', '2016-03-01', c.id,
       '阐述安全边际原则与防御型投资策略，指引投资者树立正确的投资心态与估值体系。',
       'https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787508684031', '原则', '桥水基金创始人的人生与工作法则', '瑞·达利欧', '中信出版集团', '2018-01-01', c.id,
       '通过极度求真与极度透明的文化，阐释跨越周期的人生决策框架与管理哲学。',
       'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=400', 4, 4, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787521741162', '纳瓦尔宝典', '关于财富与幸福的思考指南', '埃里克·乔根森', '中信出版社', '2022-04-01', c.id,
       '汇集硅谷投资大师纳瓦尔的智慧，探讨如何依靠杠杆获得非线性财富与内心平静。',
       'https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111280712', '卓有成效的管理者', '现代管理学之父德鲁克传世经典', '彼得·德鲁克', '机械工业出版社', '2009-11-01', c.id,
       '指出成效是可以学会的，阐释管理者如何管理时间、发挥优势、分清主次并做出有效决策。',
       'https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787111326465', '定位 (有史以来对美国营销影响最大的观念)', '营销理论与品牌心智建立', '艾·里斯 / 杰克·特劳特', '机械工业出版社', '2011-01-01', c.id,
       '阐明在信息超载的商业竞争中，如何通过差异化在潜在顾客心智中抢占第一位置。',
       'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'ECON' ON CONFLICT (isbn) DO NOTHING;

-- 3.4 数理与自然科学 (10本)
INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787535732309', '时间简史', '从大爆炸到黑洞的宇宙探索', '史蒂芬·霍金', '湖南科学技术出版社', '2010-04-01', c.id,
       '霍金向普通大众介绍宇宙学前沿的科普力作，探讨空间与时间、广义相对论与量子引力。',
       'https://images.unsplash.com/photo-1507668077129-56e32842fceb?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787521719888', '自私的基因 (40周年纪念版)', '颠覆传统生物演化观念的科学思想', '理查德·道金斯', '中信出版社', '2020-09-01', c.id,
       '从基因视角重新审视生物利他行为、亲缘选择、演化稳定策略与进化的微观驱动力。',
       'https://images.unsplash.com/photo-1532094349884-543bc11b234d?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787532724574', '费曼物理学讲义 (第1卷)', '物理大师的绝妙教学艺术', '理查德·费曼', '上海科学技术出版社', '2005-06-01', c.id,
       '以独特的洞察力系统讲授力学、辐射与热学，展现深入本质、不拘形式的物理思维。',
       'https://images.unsplash.com/photo-1509228468518-180dd4864904?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787521708813', '微积分的力量', '微积分如何改变了人类文明', '史蒂芬·斯托加茨', '中信出版集团', '2021-01-01', c.id,
       '揭示微积分如何化无限为有限，驱动从引力波探测、医学扫描到全球导航的现代科技革命。',
       'https://images.unsplash.com/photo-1507668077129-56e32842fceb?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787100013239', '哥德尔、艾舍尔、巴赫：集异璧之大成', '跨越数理逻辑、版画与音乐的奇书', '侯世达 (Douglas Hofstadter)', '商务印书馆', '1996-08-01', c.id,
       '通过多声部对话与自指悖论，探讨形式系统、递归与心智意识的产生机理。',
       'https://images.unsplash.com/photo-1532094349884-543bc11b234d?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787544318723', '万物简史', '包罗万象的现代自然科学百科全书', '比尔·布莱森', '接力出版社', '2005-02-01', c.id,
       '幽默通俗地梳理人类认识宇宙、地质演变、原子构成与生命诞生的科学探索史。',
       'https://images.unsplash.com/photo-1509228468518-180dd4864904?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787535738875', '宇宙的琴弦', '超弦理论与终极理论探索', '布赖恩·格林', '湖南科学技术出版社', '2004-03-01', c.id,
       '深入浅出介绍十一维时空、卡拉比-丘成桐空间与弦理论统一量子力学与广义相对论的构想。',
       'https://images.unsplash.com/photo-1507668077129-56e32842fceb?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787508691510', '从一到无穷大', '科学大师伽莫夫经典科学普及读物', '乔治·伽莫夫', '科学出版社', '2019-01-01', c.id,
       '从大数计数、四维几何到相对论、微观粒子，启迪青年一代数理思维的殿堂级名作。',
       'https://images.unsplash.com/photo-1532094349884-543bc11b234d?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787521714159', '生命的跃升：40亿年演化史上的十大发明', '探寻复杂生命起源的壮阔旅程', '尼克·莱恩', '中信出版集团', '2020-04-01', c.id,
       '通过生化能量与质子浓度梯度的独到理论，解释生命起源、DNA、光合作用与性别的进化。',
       'https://images.unsplash.com/photo-1509228468518-180dd4864904?w=400', 3, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787532766055', '寂静的春天', '现代环境保护运动的唤醒之作', '蕾切尔·卡森', '上海译文出版社', '2014-06-01', c.id,
       '深刻揭露化学农药对生态链的破坏，推动全球生态觉醒与现代环境科学诞生。',
       'https://images.unsplash.com/photo-1507668077129-56e32842fceb?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'SCI' ON CONFLICT (isbn) DO NOTHING;

-- 3.5 历史与人文社科 (10本)
INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787101003048', '史记 (全十册)', '史家之绝唱，无韵之离骚', '司马迁', '中华书局', '1982-11-01', c.id,
       '中国第一部纪传体通史，记述上至黄帝、下至汉武帝三千年间波澜壮阔的历史演进。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787508647357', '人类简史：从动物到上帝', '全球畅销宏观大历史力作', '尤瓦尔·赫拉利', '中信出版社', '2014-11-01', c.id,
       '通过认知革命、农业革命与科学革命三部曲，重构人类如何凭借虚构故事统治地球。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 5, 4, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787301077757', '全球通史：从史前史到21世纪 (第7版)', '全球史观里程碑著作', '斯塔夫里阿诺斯', '北京大学出版社', '2005-01-01', c.id,
       '打破西方中心主义，以全球宏观视角纵览跨大洲文明交流与世界体系形成。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787550263932', '明朝那些事儿 (增补版全七册)', '通俗历史写作经典力作', '当年明月', '北京联合出版公司', '2017-08-01', c.id,
       '以极其幽默诙谐的现代语言，全景还原大明王朝二百七十七年的政治博弈与权谋风云。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 5, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787108044730', '中国历代政治得失', '一代国学大师钱穆史学精粹', '钱穆', '生活·读书·新知三联书店', '2012-07-01', c.id,
       '专题评析汉、唐、宋、明、清五朝政府组织、考试制度、赋税制度与兵役制度之利弊得失。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787108009791', '万历十五年', '微观叙事解构明代制度困局', '黄仁宇', '生活·读书·新知三联书店', '2006-08-01', c.id,
       '通过明神宗、张居正、海瑞、戚继光等人的命运交织，以大历史观透视中国传统官僚政治死结。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787521730340', '枪炮、病菌与钢铁：人类社会的命运', '地理环境决定论经典', '贾雷德·戴蒙德', '中信出版社', '2022-01-01', c.id,
       '跨越生态学与人类学，解释欧亚大陆为何因地理轴线、物种驯化而在近代统治全球。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787101007893', '资治通鉴 (标点精校全二十册)', '鉴于往事，有资于治道', '司马光', '中华书局', '2011-04-01', c.id,
       '编年体通史巨著，记述战国至五代一千三百余年兴衰成败与政治军事智慧。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787108041494', '叫魂：1768年中国妖术大恐慌', '微观史学经典范本', '孔飞力 (Philip A. Kuhn)', '生活·读书·新知三联书店', '2014-05-01', c.id,
       '乾隆盛世下的一场虚构割辫妖术恐慌，剖析清代专制权力运作机制与官僚自我防护机制。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 3, 2, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

INSERT INTO books (isbn, title, subtitle, author, publisher_name, publish_date, category_id, description, cover_url, total_copies, available_copies, status, storage_type)
SELECT '9787208171299', '置身事内：中国政府与经济发展', '通俗读懂中国当下微观与宏观经济运作', '兰小欢', '上海人民出版社', '2021-08-01', c.id,
       '聚焦地方政府与投融资行为，清晰剖析土地财政、招商引资与中国经济转型的现实逻辑。',
       'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400', 4, 3, 'ACTIVE', 'LOCAL'
FROM categories c WHERE c.code = 'HIST' ON CONFLICT (isbn) DO NOTHING;

-- ----------------------------------------------------------------------
-- 4. 自动生成物理副本 (book_copies)
-- ----------------------------------------------------------------------
INSERT INTO book_copies (book_id, barcode, location, status, acquired_at, created_at, updated_at)
SELECT b.id, 
       'BC90' || LPAD(b.id::text, 4, '0') || '01', 
       '图书馆三楼自科流通阅览室 ' || (b.id % 20 + 1) || '架A面', 
       CASE WHEN b.available_copies = 0 THEN 'BORROWED' ELSE 'AVAILABLE' END, 
       CURRENT_DATE - INTERVAL '100 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM books b
WHERE b.isbn IN (
    '9787111544937', '9787111407010', '9787111075752', '9787115216878', '9787115508645',
    '9787111641247', '9787115531407', '9787121443657', '9787111464747', '9787111612728',
    '9787111599715', '9787115461475', '9787020002207', '9787544253994', '9787536692909',
    '9787020083985', '9787530216781', '9787506365437', '9787544258609', '9787208061644',
    '9787532771691', '9787532725694', '9787301258660', '9787100018159', '9787508633558',
    '9787508640709', '9787100057936', '9787115421677', '9787508684031', '9787521741162',
    '9787111280712', '9787111326465', '9787535732309', '9787521719888', '9787532724574',
    '9787521708813', '9787100013239', '9787544318723', '9787535738875', '9787508691510',
    '9787521714159', '9787532766055', '9787101003048', '9787508647357', '9787301077757',
    '9787550263932', '9787108044730', '9787108009791', '9787521730340', '9787101007893',
    '9787108041494', '9787208171299'
)
ON CONFLICT (barcode) DO NOTHING;

-- 生成第二副本
INSERT INTO book_copies (book_id, barcode, location, status, acquired_at, created_at, updated_at)
SELECT b.id, 
       'BC90' || LPAD(b.id::text, 4, '0') || '02', 
       '图书馆四楼社科借阅室 ' || (b.id % 20 + 1) || '架B面', 
       'AVAILABLE', 
       CURRENT_DATE - INTERVAL '90 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM books b
WHERE b.total_copies >= 2
  AND b.isbn IN (
    '9787111544937', '9787111407010', '9787111075752', '9787115216878', '9787115508645',
    '9787111641247', '9787115531407', '9787121443657', '9787111464747', '9787111612728',
    '9787111599715', '9787115461475', '9787020002207', '9787544253994', '9787536692909',
    '9787020083985', '9787530216781', '9787506365437', '9787544258609', '9787208061644',
    '9787532771691', '9787532725694', '9787301258660', '9787100018159', '9787508633558',
    '9787508640709', '9787100057936', '9787115421677', '9787508684031', '9787521741162',
    '9787111280712', '9787111326465', '9787535732309', '9787521719888', '9787532724574',
    '9787521708813', '9787100013239', '9787544318723', '9787535738875', '9787508691510',
    '9787521714159', '9787532766055', '9787101003048', '9787508647357', '9787301077757',
    '9787550263932', '9787108044730', '9787108009791', '9787521730340', '9787101007893',
    '9787108041494', '9787208171299'
)
ON CONFLICT (barcode) DO NOTHING;

-- ----------------------------------------------------------------------
-- 5. 预置真实场景借阅流水 (已归还、正在借阅、故意逾期)
-- ----------------------------------------------------------------------
-- 5.1 已正常归还的历史记录
INSERT INTO borrow_records (
    record_no, user_id, book_id, copy_id, borrow_rule_id, 
    borrowed_at, due_at, returned_at, renew_count, status, fine_amount, remark
)
SELECT 'BR2026090100001', u.id, b.id, c.id, u.borrow_rule_id,
       CURRENT_TIMESTAMP - INTERVAL '35 days', 
       CURRENT_TIMESTAMP - INTERVAL '5 days', 
       CURRENT_TIMESTAMP - INTERVAL '8 days', 
       0, 'RETURNED', 0.00, '读者期初按期归还'
FROM users u, books b, book_copies c
WHERE u.username = 'student_demo'
  AND b.isbn = '9787111407010' -- 《算法导论》
  AND c.book_id = b.id
ORDER BY c.id ASC LIMIT 1
ON CONFLICT (record_no) DO NOTHING;

-- 5.2 正在借阅中（正常期限内）
INSERT INTO borrow_records (
    record_no, user_id, book_id, copy_id, borrow_rule_id, 
    borrowed_at, due_at, returned_at, renew_count, status, fine_amount, remark
)
SELECT 'BR2026091000002', u.id, b.id, c.id, u.borrow_rule_id,
       CURRENT_TIMESTAMP - INTERVAL '5 days', 
       CURRENT_TIMESTAMP + INTERVAL '25 days', 
       NULL, 0, 'BORROWING', 0.00, '正常借阅中'
FROM users u, books b, book_copies c
WHERE u.username = 'student_demo'
  AND b.isbn = '9787115216878' -- 《代码整洁之道》
  AND c.book_id = b.id
ORDER BY c.id ASC LIMIT 1
ON CONFLICT (record_no) DO NOTHING;

-- 5.3 故意制造逾期记录（用于答辩演示逾期预警与罚款计算）
INSERT INTO borrow_records (
    record_no, user_id, book_id, copy_id, borrow_rule_id, 
    borrowed_at, due_at, returned_at, renew_count, status, fine_amount, remark
)
SELECT 'BR2026081500003', u.id, b.id, c.id, u.borrow_rule_id,
       CURRENT_TIMESTAMP - INTERVAL '45 days', 
       CURRENT_TIMESTAMP - INTERVAL '15 days', 
       NULL, 0, 'OVERDUE', 1.50, '已逾期15天，需提醒催还'
FROM users u, books b, book_copies c
WHERE u.username = 'student_demo'
  AND b.isbn = '9787536692909' -- 《三体全集》
  AND c.book_id = b.id
ORDER BY c.id ASC LIMIT 1
ON CONFLICT (record_no) DO NOTHING;

-- ----------------------------------------------------------------------
-- 6. 预置真实排队预约记录 (展示零库存排队与到馆待取)
-- ----------------------------------------------------------------------
-- 6.1 《深入理解计算机系统》当前零库存，student_demo 正在排队等待第 1 位
INSERT INTO reservations (
    reservation_no, user_id, book_id, status, queue_position, reserved_at
)
SELECT 'RSV2026091700001', u.id, b.id, 'WAITING', 1, CURRENT_TIMESTAMP - INTERVAL '2 hours'
FROM users u, books b
WHERE u.username = 'student_demo'
  AND b.isbn = '9787111544937' -- 《深入理解计算机系统》
ON CONFLICT (reservation_no) DO NOTHING;

-- 6.2 《操作系统概念》预约已就绪，等待 student_demo 到馆自提 (48小时保留期)
INSERT INTO reservations (
    reservation_no, user_id, book_id, status, queue_position, reserved_at, ready_at, expired_at
)
SELECT 'RSV2026091600002', u.id, b.id, 'READY', 0, 
       CURRENT_TIMESTAMP - INTERVAL '24 hours',
       CURRENT_TIMESTAMP - INTERVAL '2 hours',
       CURRENT_TIMESTAMP + INTERVAL '46 hours'
FROM users u, books b
WHERE u.username = 'student_demo'
  AND b.isbn = '9787111612728' -- 《操作系统概念》
ON CONFLICT (reservation_no) DO NOTHING;

-- ----------------------------------------------------------------------
-- 7. 预置多维度站内消息通知 (借还、临期、预约就绪、系统公告)
-- ----------------------------------------------------------------------
-- 7.1 借阅成功通知
INSERT INTO notifications (user_id, title, content, type, is_read, related_entity_type, related_entity_id, created_at)
SELECT u.id, '图书借阅成功通知', '您于近日成功借阅《代码整洁之道》，应还日期为 25 天后，请合理安排阅读时间。',
       'BORROW_SUCCESS', TRUE, 'BOOK', b.id, CURRENT_TIMESTAMP - INTERVAL '5 days'
FROM users u, books b WHERE u.username = 'student_demo' AND b.isbn = '9787115216878'
ON CONFLICT DO NOTHING;

-- 7.2 临期提醒通知
INSERT INTO notifications (user_id, title, content, type, is_read, related_entity_type, related_entity_id, created_at)
SELECT u.id, '借阅临期归还提醒', '您借阅的《三体全集》即将超期，请尽快登录系统办理顺延续借或前往馆内归还。',
       'BORROW_DUE_REMIND', TRUE, 'BOOK', b.id, CURRENT_TIMESTAMP - INTERVAL '16 days'
FROM users u, books b WHERE u.username = 'student_demo' AND b.isbn = '9787536692909'
ON CONFLICT DO NOTHING;

-- 7.3 预约到书可取就绪通知 (未读，展示未读小红点 Badge)
INSERT INTO notifications (user_id, title, content, type, is_read, related_entity_type, related_entity_id, created_at)
SELECT u.id, '预约图书已就绪待领', '您预约的《操作系统概念》已由读者归还并完成入库就绪，自提保留期至 46 小时后，请及时前往总服务台出示条码取书。',
       'RESERVATION_READY', FALSE, 'RESERVATION', 1, CURRENT_TIMESTAMP - INTERVAL '2 hours'
FROM users u WHERE u.username = 'student_demo'
ON CONFLICT DO NOTHING;

-- 7.4 馆长系统广播公告
INSERT INTO notifications (user_id, title, content, type, is_read, related_entity_type, related_entity_id, created_at)
SELECT u.id, '图书馆 2026 秋季学期开学读者借阅须知', '亲爱的师生读者：新学期全馆已实现智能流通全覆盖，请按规定爱护图书，欢迎使用 AI 导读功能！',
       'SYSTEM_ANNOUNCEMENT', FALSE, 'SYSTEM', 0, CURRENT_TIMESTAMP - INTERVAL '1 days'
FROM users u WHERE u.username = 'student_demo'
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------
-- 8. 预置 AI 导读持久化结构数据 (展示大模型防幻觉结构化解析)
-- ----------------------------------------------------------------------
INSERT INTO ai_book_insights (book_id, summary, key_topics, target_reader, reading_guide, model_name, generated_at)
SELECT b.id,
       '本书从程序员的视角出发，深度剖析计算机底层硬件与系统软件协同工作的内在本质，彻底打破软硬件知识鸿沟。',
       '["汇编语言", "虚拟内存管理", "存储体系结构", "并发编程", "链接器与加载器"]'::jsonb,
       '计算机相关专业本科生/研究生、底层基础设施开发工程师、系统架构师',
       '建议先攻读第 1~3 章建立整体认知，重点攻坚第 6 章存储山与第 9 章虚拟内存，结合配套 7 大核心实验 Lab 动手实操。',
       'deepseek-chat', CURRENT_TIMESTAMP
FROM books b WHERE b.isbn = '978711544937' OR b.isbn = '9787111544937'
ON CONFLICT (book_id) DO NOTHING;

INSERT INTO ai_book_insights (book_id, summary, key_topics, target_reader, reading_guide, model_name, generated_at)
SELECT b.id,
       '全球公认算法权威教材，系统阐明经典算法设计原理、时间空间渐近复杂度数学分析及高级数据结构构造。',
       '["渐近分析", "分治策略", "动态规划", "贪心选择", "图论网络流", "NP完全性"]'::jsonb,
       '计算机科学与软件工程专业学生、准备技术面试与算法竞赛的开发者',
       '按部就班阅读前缀基础，每一章重点理解循环不变式证明，尝试独立完成课后典型习题。',
       'deepseek-chat', CURRENT_TIMESTAMP
FROM books b WHERE b.isbn = '9787111407010'
ON CONFLICT (book_id) DO NOTHING;

-- ----------------------------------------------------------------------
-- 9. 预置 AI 推荐点击与转化日志 (用于馆员工作台 CTR 与 BCR 漏斗展示)
-- ----------------------------------------------------------------------
INSERT INTO ai_recommendation_logs (user_id, book_id, recommendation_source, score, scene, clicked, borrowed, created_at)
SELECT u.id, b.id, 'CONTENT_BASED', 96.50, 'HOME_RECOMMEND', TRUE, TRUE, CURRENT_TIMESTAMP - INTERVAL '10 days'
FROM users u, books b WHERE u.username = 'student_demo' AND b.isbn = '9787115216878'
ON CONFLICT DO NOTHING;

INSERT INTO ai_recommendation_logs (user_id, book_id, recommendation_source, score, scene, clicked, borrowed, created_at)
SELECT u.id, b.id, 'BEHAVIOR_COLLABORATIVE', 88.00, 'HOME_RECOMMEND', TRUE, FALSE, CURRENT_TIMESTAMP - INTERVAL '5 days'
FROM users u, books b WHERE u.username = 'student_demo' AND b.isbn = '9787532771691'
ON CONFLICT DO NOTHING;

INSERT INTO ai_recommendation_logs (user_id, book_id, recommendation_source, score, scene, clicked, borrowed, created_at)
SELECT u.id, b.id, 'POPULARITY', 82.30, 'HOME_RECOMMEND', FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM users u, books b WHERE u.username = 'student_demo' AND b.isbn = '9787508647357'
ON CONFLICT DO NOTHING;
