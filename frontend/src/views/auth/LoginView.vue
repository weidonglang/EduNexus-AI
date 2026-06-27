<script setup lang="ts">
// 登录页面。
// 用户输入账号密码后调用后端登录接口，成功后保存 token、用户信息和角色，
// 再进入首页。页面不展示测试账号，避免演示时泄露敏感信息。
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { BookOpenCheck, DatabaseZap, GraduationCap, LockKeyhole, ShieldCheck, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'

const auth = useAuthStore()
const menu = useMenuStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await menu.loadMenus(true)
    ElMessage.success('登录成功')
    await router.push((route.query.redirect as string) || '/dashboard')
  } catch {
    ElMessage.error('登录失败，请检查账号、密码或后端服务状态')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-hero">
      <div class="login-hero-content">
        <span class="school-label">教学综合信息服务平台 · 教务服务入口</span>
        <h1>教学综合信息服务平台</h1>
        <p>面向学生、教师与教务管理人员，统一承载学籍、选课、课表、成绩、考试、通知和教学评价等核心业务。</p>
        <div class="login-highlights">
          <span><GraduationCap :size="16" /> 多角色服务</span>
          <span><BookOpenCheck :size="16" /> 教学业务闭环</span>
          <span><DatabaseZap :size="16" /> Redis 抢课支撑</span>
        </div>
        <div class="login-hero-metrics">
          <article>
            <strong>3</strong>
            <span>用户角色</span>
          </article>
          <article>
            <strong>20+</strong>
            <span>教务功能</span>
          </article>
          <article>
            <strong>10k</strong>
            <span>压测账号</span>
          </article>
        </div>
      </div>
    </section>

    <section class="login-card">
      <div class="login-card-title">
        <ShieldCheck :size="22" />
        <div>
          <h2>账号登录</h2>
          <p>请输入已分配的学号、工号或管理员账号</p>
        </div>
      </div>

      <el-form class="login-form" :model="form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model.trim="form.username" placeholder="学号 / 工号 / 管理员账号" size="large" autocomplete="username">
            <template #prefix><UserRound :size="18" /></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" placeholder="请输入密码" size="large" type="password" show-password autocomplete="current-password">
            <template #prefix><LockKeyhole :size="18" /></template>
          </el-input>
        </el-form-item>
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="login-security">
        <div>
          <strong>安全提示</strong>
          <span>请勿在公共设备保存密码，离开系统前及时退出登录。</span>
        </div>
      </div>
    </section>
  </main>
</template>
