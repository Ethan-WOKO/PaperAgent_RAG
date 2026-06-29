<template>
  <NLayout class="app-layout">
    <NLayoutHeader bordered class="app-header">
      <div class="app-header__brand" @click="router.push('/chat')">
        <div class="app-header__logo">研</div>
        <div>
          <div class="app-title">研伴 Agent</div>
          <div class="app-subtitle">Research co-pilot workspace</div>
        </div>
      </div>

      <nav class="app-header__nav" aria-label="主导航">
        <NButton
          v-for="item in navItems"
          :key="item.path"
          quaternary
          round
          class="app-nav-button"
          :class="{ 'app-nav-button--active': isActiveNav(item.path) }"
          @click="router.push(item.path)"
        >
          <span class="app-nav-button__icon">{{ item.icon }}</span>
          {{ item.label }}
        </NButton>
      </nav>

      <NSpace align="center" :size="12">
        <NButton quaternary round class="theme-toggle-button" @click="toggleTheme">
          {{ isDark ? '浅色' : '深色' }}
        </NButton>
        <div class="app-user-chip">{{ authStore.currentUser?.username || '未登录' }}</div>
        <NButton tertiary type="error" round @click="logout">退出</NButton>
      </NSpace>
    </NLayoutHeader>

    <NLayoutContent content-class="app-content-shell">
      <slot />
    </NLayoutContent>
  </NLayout>
</template>

<script setup lang="ts">
import { NButton, NLayout, NLayoutContent, NLayoutHeader, NSpace } from 'naive-ui';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useTheme } from '@/composables/useTheme';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const { isDark, toggleTheme } = useTheme();

const navItems = [
  { label: '对话', path: '/chat', icon: '✦' },
  { label: '论文', path: '/paper', icon: '✎' },
  { label: '知识库', path: '/knowledge-base', icon: '▣' },
  { label: '检索调试', path: '/knowledge-base/search-debug', icon: '⌕' },
  { label: '设置', path: '/settings', icon: '⚙' },
];

function isActiveNav(path: string) {
  if (path === '/knowledge-base') {
    return route.path === path;
  }
  return route.path === path || route.path.startsWith(`${path}/`);
}

async function logout() {
  authStore.clear();
  await router.push('/login');
}
</script>
