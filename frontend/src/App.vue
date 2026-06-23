<template>
  <div id="app">
    <div class="chat-app">
      <div class="main-container">
        <div class="sidebar">
          <div class="sidebar-header-fixed">
            智能移动设备管理助手
          </div>
          <div class="nav-list">
            <div :class="['nav-item', { active: currentNav === 'chat' }]" @click="switchToChat">
              开始聊天
            </div>
            <div :class="['nav-item', { active: currentNav === 'favorite' }]" @click="openFavoritesPage">
              设备收藏 <span v-if="unreadFavoritesCount > 0" class="favorite-badge">{{ unreadFavoritesCount }}</span>
            </div>
            <div :class="['nav-item', { active: currentNav === 'mydevice' }]" @click="openMyDevice">
              我的设备
            </div>
          </div>
          <div class="sidebar-settings" v-if="isLoggedIn">
            <div class="settings-combined" @click="openSettingsModal" title="设置">
              <div class="settings-gear">
                <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="3"></circle>
                  <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                </svg>
              </div>
              <div class="settings-pill">
                <span class="settings-nickname">{{ displayNickname }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="showHistoryOverlay" class="history-overlay" @click="showHistoryOverlay = false">
          <div class="history-panel" @click.stop>
            <div class="history-panel-header">
              <span style="font-weight: bold;">历史对话</span>
              <button class="new-chat-btn" @click="createNewChat">+ 新建</button>
            </div>
            <div class="history-list">
              <div v-if="chatHistory.length === 0" class="empty-history">
                暂无历史记录
              </div>
              <div v-for="(chat, index) in chatHistory"
                   :key="index"
                   :class="['history-item', { active: currentChatIndex === index, 'only-one': chatHistory.length === 1 }]"
                   @click="switchChat(index)">
                <div class="history-item-header">
                  <span class="history-item-text">{{ chat.title || '新对话' }}</span>
                  <span class="history-item-time">{{ formatHistoryTime(chat.timestamp) }}</span>
                </div>
                <div class="delete-area" @click.stop="deleteChat(index)" title="删除对话">删除</div>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-area" v-if="currentNav === 'chat'">
          <div v-if="showWarrantyBanner && warrantyNearbyDevices.length > 0" class="warranty-marquee-banner">
            <div class="warranty-marquee-content">
              <span class="marquee-title">⚠️ 保修即将到期提醒：</span>
              <span v-for="(dev, i) in warrantyNearbyDevices" :key="dev.id" class="marquee-item">
                <span @click="navigateToMyDeviceAndDetail(dev.id)" style="cursor: pointer; font-weight: 600;">{{ dev.deviceName }}</span> {{ dev.daysRemaining <= 0 ? '已过期' : '剩余 ' + dev.daysRemaining + ' 天' }}
                <span v-if="i < warrantyNearbyDevices.length - 1" style="margin: 0 20px;">•</span>
              </span>
            </div>
            <button class="warranty-marquee-close" @click="showWarrantyBanner = false" title="关闭提醒">×</button>
          </div>
          <div class="chat-top-bar">
            <button class="history-toggle-btn" @click="showHistoryOverlay = !showHistoryOverlay" :title="showHistoryOverlay ? '隐藏历史' : '显示历史'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline :points="showHistoryOverlay ? '9 18 15 12 9 6' : '15 18 9 12 15 6'"></polyline>
              </svg>
            </button>
            <div class="api-status">
              <span class="status-dot" :class="apiStatusClass"></span>
              <span>{{ apiStatusText }}</span>
              <button class="api-control-btn"
                      :class="apiConnected ? 'disconnect' : 'connect'"
                      @click="toggleApiConnection"
                      :disabled="apiChecking">
                {{ apiConnected ? '断开' : '重连' }}
              </button>
            </div>
            <div class="header-right">
              <div v-if="isLoggedIn" class="user-menu">
                <div class="user-info" @click="showUserMenu = !showUserMenu">
                  <img v-if="currentUser.avatarData" :src="currentUser.avatarData" class="user-avatar" />
                  <div v-else class="user-avatar-placeholder">{{ currentUser.nickname?.charAt(0) }}</div>
                  <span>{{ currentUser.nickname }}</span>
                  <svg class="dropdown-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M6 9l6 6 6-6"/>
                  </svg>
                </div>
                <div v-if="showUserMenu" class="user-dropdown">
                  <button @click="handleLogout">退出登录</button>
                </div>
              </div>
              <button v-else class="login-btn" @click="openAuthModal">登录</button>
            </div>
          </div>
          <div class="chat-messages" ref="messagesContainer">
            <div v-if="messages.length === 0" class="welcome-screen">
              <div class="welcome-title">欢迎，我是你的智能助手</div>
              <div class="welcome-title">有什么我能帮你的吗？</div>
              <div class="suggestion-buttons">
                <div class="suggestion-row">
                  <button class="suggestion-btn" @click="useSuggestion('推荐一款2000-4000元的手机')">推荐一款2000-4000元的手机</button>
                  <button class="suggestion-btn" @click="useSuggestion('对比iPhone 15和小米14')">对比iPhone 15和小米14</button>
                  <button class="suggestion-btn" @click="useSuggestion('帮我推荐一款续航好的手表')">帮我推荐一款续航好的手表</button>
                </div>
                <div class="suggestion-row">
                  <button class="suggestion-btn" @click="useSuggestion('推荐一款降噪耳机，预算500-1000')">推荐一款降噪耳机，预算500-1000</button>
                  <button class="suggestion-btn" @click="useSuggestion('帮我看看iPad Pro和MatePad Pro哪个好')">帮我看看iPad Pro和MatePad Pro哪个好</button>
                  <button class="suggestion-btn" @click="useSuggestion('推荐一款适合拍照的手机')">推荐一款适合拍照的手机</button>
                </div>
                <div class="suggestion-row">
                  <button class="suggestion-btn" @click="useSuggestion('苹果手机和华为手机怎么选')">苹果手机和华为手机怎么选</button>
                  <button class="suggestion-btn" @click="useSuggestion('预算2000元，推荐什么平板')">预算2000元，推荐什么平板</button>
                  <button class="suggestion-btn" @click="useSuggestion('智能手表有什么推荐')">智能手表有什么推荐</button>
                </div>
              </div>
            </div>
            <div v-else>
              <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.type]">
                <div v-if="msg.type === 'user'" class="message-row-user">
                  <button class="recall-btn" title="撤回此消息" @click="recallMessage(index)">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
                  </button>
                  <div class="message-wrapper">
                    <div class="message-time">{{ formatMessageTime(msg.timestamp) }}</div>
                    <div class="message-content">
                      <div v-if="msg.imageDataUrl" class="message-image">
                        <img :src="msg.imageDataUrl" alt="用户上传图片" @click="previewImage(msg.imageDataUrl)" />
                      </div>
                      <div v-if="msg.content && msg.content !== '[图片上传]'" v-html="parseMarkdown(msg.content)"></div>
                    </div>
                  </div>
                </div>
                <div v-else class="message-wrapper">
                  <div class="message-time">{{ formatMessageTime(msg.timestamp) }}</div>
                  <div class="message-content">
                    <div v-if="msg.imageDataUrl" class="message-image">
                      <img :src="msg.imageDataUrl" alt="用户上传图片" @click="previewImage(msg.imageDataUrl)" />
                    </div>
                    <div v-if="msg.content && msg.content !== '[图片上传]'" v-html="parseMarkdown(msg.content)"></div>
                  </div>
                  <div v-if="msg.type === 'ai' && msg.showFavorite" class="message-actions">
                    <template v-if="extractRecommendDevices(msg.content).length > 0">
                      <button 
                        v-for="device in extractRecommendDevices(msg.content)" 
                        :key="device"
                        class="favorite-btn"
                        :class="{ favorited: isFavorited(device) }"
                        @click="toggleFavorite(device, extractDeviceInfo(msg.content, device).category, extractDeviceInfo(msg.content, device))"
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                        </svg>
                        {{ isFavorited(device) ? '已收藏' : '收藏' }} {{ device }}
                      </button>
                    </template>
                    <template v-else-if="msg.deviceName && !isFavorited(msg.deviceName)">
                      <button class="favorite-btn" @click="toggleFavoriteMsg(msg)">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                        </svg>
                        收藏 {{ msg.deviceName }}
                      </button>
                    </template>
                  </div>
                </div>
              </div>
              <div v-if="loading" class="message ai">
                <div class="message-content">
                  <span class="loading-dots"><span></span><span></span><span></span></span>
                </div>
              </div>
            </div>
          </div>
          <div class="device-list" v-if="devices.length > 0">
            <span v-for="device in devices" :key="device" class="device-item">{{ device }}</span>
          </div>
          <div class="chat-input-area">
            <div class="input-row">
              <label class="upload-btn" title="上传SN码图片">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                  <circle cx="8.5" cy="8.5" r="1.5"></circle>
                  <polyline points="21 15 16 10 5 21"></polyline>
                </svg>
                <input type="file" class="hidden" accept="image/*" @change="handleImageUpload">
              </label>
              <button class="custom-device-btn" title="个性化选机" @click="openDevicePrefModal">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
          <line x1="12" y1="22" x2="12" y2="19"></line>
          <line x1="12" y1="5" x2="12" y2="2"></line>
          <line x1="22" y1="12" x2="19" y2="12"></line>
          <line x1="5" y1="12" x2="2" y2="12"></line>
          <line x1="19.07" y1="19.07" x2="16.95" y2="16.95"></line>
          <line x1="7.05" y1="7.05" x2="4.93" y2="4.93"></line>
          <line x1="19.07" y1="4.93" x2="16.95" y2="7.05"></line>
          <line x1="7.05" y1="16.95" x2="4.93" y2="19.07"></line>
        </svg>
      </button>
              <button class="voice-btn" :class="{ listening: isListening }" @click="toggleVoiceInput" :title="isListening ? '点击停止录音' : '语音输入'" :disabled="sending">
                <svg v-if="!isListening" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
                  <line x1="12" y1="19" x2="12" y2="23"></line>
                  <line x1="8" y1="23" x2="16" y2="23"></line>
                </svg>
                <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <rect x="6" y="4" width="4" height="16" rx="1"/>
                  <rect x="14" y="4" width="4" height="16" rx="1"/>
                </svg>
              </button>
              <input type="text" class="chat-input" v-model="inputMessage" @keyup.enter="sendMessage" placeholder="请输入您的问题..." :disabled="sending">
              <button v-if="sending" class="stop-button" @click="stopGeneration" title="停止生成">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <rect x="4" y="4" width="16" height="16" rx="2"/>
                </svg>
              </button>
              <button v-else class="send-button" @click="sendMessage" :disabled="!inputMessage.trim()">发送</button>
            </div>
          </div>
        </div>

        <div class="favorites-page" v-if="currentNav === 'favorite'">
          <div class="favorites-page-header">
            <h2>设备收藏</h2>
            <span class="favorites-count">共 {{ favoriteTotalCount }} 个设备</span>
          </div>
          <div v-if="favoritesLoading" class="favorites-loading">加载中...</div>
          <div v-else-if="Object.keys(favoritesByCategory).length === 0" class="empty-favorites-page">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
            <p>暂无收藏的设备</p>
            <span>在对话中收藏设备后将会显示在这里</span>
          </div>
          <div v-else class="favorites-content">
            <div v-for="(devices, category) in favoritesByCategory" :key="category" class="favorites-category">
              <div class="favorites-category-header">
                <span class="category-icon">{{ getCategoryIcon(category) }}</span>
                <span class="category-name">{{ category }}</span>
                <span class="category-count">{{ devices.length }} 个设备</span>
              </div>
              <div class="favorites-grid">
                <div v-for="device in devices" :key="device.id" class="favorite-card">
                  <div class="favorite-card-header">
                    <div class="device-category-icon">{{ getCategoryIcon(category) }}</div>
                    <button class="favorite-card-remove" @click="removeFavorite(device.deviceName)" title="取消收藏">✕</button>
                  </div>
                  <div class="favorite-card-body">
                    <div class="device-name">{{ device.deviceName }}</div>
                    <div v-if="device.brand" class="device-brand">{{ device.brand }}</div>
                    <div v-if="device.price" class="device-price">{{ device.price }}</div>
                    <div v-if="device.specs" class="device-specs">{{ device.specs }}</div>
                  </div>
                  <div class="favorite-card-footer">
                    <span class="favorite-time">{{ formatFavoriteTime(device.createdAt) }}</span>
                    <a v-if="device.jdUrl" :href="device.jdUrl" target="_blank" class="jd-link">京东查看</a>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="mydeviceSidebarOpen" class="history-overlay" @click="mydeviceSidebarOpen = false">
          <div class="history-panel mydevice-panel" @click.stop>
            <div class="history-panel-header">
              <span style="font-weight: bold;">设备列表</span>
            </div>
            <div class="mydevice-sidebar-list" v-if="myDevices.length > 0">
              <div
                v-for="dev in myDevices"
                :key="dev.id"
                :class="['mydevice-nav-item', { active: selectedDeviceId === dev.id }]"
                @click="viewDeviceDetail(dev.id); mydeviceSidebarOpen = false"
              >
                <span class="mydevice-nav-icon">{{ getCategoryIcon(dev.deviceCategory || '手机') }}</span>
                <span class="mydevice-nav-name">{{ dev.deviceName }}</span>
              </div>
            </div>
            <div class="mydevice-sidebar-empty" v-else>
              暂无设备
            </div>
          </div>
        </div>

        <div class="mydevice-page" v-if="currentNav === 'mydevice'">
          <div class="mydevice-main">
            <div class="mydevice-content-area">
              <div class="mydevice-top-bar">
                <button class="mydevice-expand-btn" @click="mydeviceSidebarOpen = !mydeviceSidebarOpen" :title="mydeviceSidebarOpen ? '收起设备列表' : '展开设备列表'">
                  <svg v-if="!mydeviceSidebarOpen" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
                  <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
                </button>
                <div class="mydevice-search-box">
                  <svg class="mydevice-search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                  <input
                    v-model="mydeviceSearchText"
                    type="text"
                    placeholder="搜索设备名称..."
                    @input="onMyDeviceSearch"
                  />
                </div>
                <button class="mydevice-add-btn" @click="openAddDeviceModal">+ 新增设备</button>
              </div>

              <div v-if="warrantyNearbyDevices.length > 0 && !selectedDeviceId" class="warranty-nearby-banner">
                <div class="warranty-nearby-title">
                  <span class="warranty-nearby-icon">⚠️</span>
                  保修即将到期提醒
                  <span class="warranty-nearby-count">{{ warrantyNearbyDevices.length }} 台</span>
                </div>
                <div class="warranty-nearby-list">
                  <div v-for="dev in warrantyNearbyDevices" :key="dev.id" class="warranty-nearby-item">
                    <span
                      class="warranty-nearby-device-name"
                      @click="navigateToMyDeviceAndDetail(dev.id)"
                      style="cursor: pointer;"
                    >{{ dev.deviceName }}</span>
                    <span class="warranty-nearby-days" :class="{ 'urgent': dev.daysRemaining <= 7 }">
                      {{ dev.daysRemaining <= 0 ? '已过期' : '剩余 ' + dev.daysRemaining + ' 天' }}
                    </span>
                  </div>
                </div>
              </div>

              <div class="mydevice-page-header" v-if="!selectedDeviceId">
                <h2>我的设备</h2>
                <span class="mydevice-count">{{ myDevices.length }} 台设备</span>
              </div>

              <div v-if="!selectedDeviceId" class="mydevice-cards" v-show="myDevices.length > 0">
                <div
                  v-for="dev in myDevices"
                  :key="dev.id"
                  class="mydevice-card"
                  @click="viewDeviceDetail(dev.id)"
                >
                  <div class="mydevice-card-icon">{{ getCategoryIcon(dev.deviceCategory || '手机') }}</div>
                  <div class="mydevice-card-info">
                    <div class="mydevice-card-name">
                      {{ dev.deviceName }}
                      <span v-if="isWarrantyExpired(dev)" class="warranty-expired-badge">已过保</span>
                    </div>
                    <div class="mydevice-card-meta">
                      <span v-if="dev.brand" class="mydevice-card-brand">{{ dev.brand }}</span>
                      <span v-if="dev.ram || dev.rom" class="mydevice-card-storage">{{ dev.ram || '' }}{{ dev.ram && dev.rom ? '+' : '' }}{{ dev.rom || '' }}</span>
                    </div>
                    <div class="mydevice-card-sn">SN: {{ dev.snCode }}</div>
                  </div>
                </div>
              </div>

              <div class="mydevice-empty" v-if="!selectedDeviceId && myDevices.length === 0">
                <div class="mydevice-empty-icon">📱</div>
                <p>暂无设备，点击右上角"新增设备"添加您的第一台设备</p>
              </div>

              <div v-if="selectedDeviceId" class="mydevice-detail">
                <div class="mydevice-detail-header">
                  <button class="mydevice-back-btn" @click="selectedDeviceId = null">← 返回列表</button>
                  <button class="mydevice-delete-btn" @click="confirmDeleteDevice(selectedDeviceId)">删除设备</button>
                </div>
                <div class="mydevice-detail-card" v-if="selectedDevice">
                  <div class="detail-icon">{{ getCategoryIcon(selectedDevice.deviceCategory || '手机') }}</div>
                  <h2 class="detail-name">{{ selectedDevice.deviceName }}</h2>
                  <div class="detail-table">
                    <div class="detail-row">
                      <span class="detail-label">品牌</span>
                      <span class="detail-value">{{ selectedDevice.brand || '未填写' }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">产品类型</span>
                      <span class="detail-value">{{ selectedDevice.deviceCategory || '未填写' }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">RAM</span>
                      <span class="detail-value">{{ selectedDevice.ram || '未填写' }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">ROM</span>
                      <span class="detail-value">{{ selectedDevice.rom || '未填写' }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">SN码</span>
                      <span class="detail-value sn-value">{{ selectedDevice.snCode }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">激活日期</span>
                      <span class="detail-value">{{ selectedDevice.activationDate }}</span>
                    </div>
                    <div class="detail-row highlight">
                      <span class="detail-label">保修截止日期</span>
                      <span class="detail-value warranty-value">{{ selectedDevice.warrantyExpireDate }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="showAddDeviceModal" class="modal-overlay" @click="showAddDeviceModal = false">
            <div class="add-device-modal" @click.stop>
              <div class="modal-header">
                <h2>新增设备</h2>
                <button class="modal-close" @click="showAddDeviceModal = false">×</button>
              </div>
              <div class="add-device-form">
                <div class="form-group">
                  <label>设备名称 <span class="required">*</span></label>
                  <input v-model="newDevice.deviceName" type="text" placeholder="例如：iPhone 16 Pro Max" />
                </div>
                <div class="form-group">
                  <label>SN码 <span class="required">*</span></label>
                  <input v-model="newDevice.snCode" type="text" placeholder="输入设备SN码" />
                </div>
                <div class="form-group">
                  <label>激活日期 <span class="required">*</span></label>
                  <input v-model="newDevice.activationDate" type="date" />
                </div>
                <div class="form-group">
                  <label>品牌</label>
                  <input v-model="newDevice.brand" type="text" placeholder="例如：Apple" />
                </div>
                <div class="form-group">
                  <label>产品类型</label>
                  <select v-model="newDevice.deviceCategory">
                    <option value="">请选择</option>
                    <option v-for="cat in productCategories" :key="cat" :value="cat">{{ cat }}</option>
                  </select>
                </div>
                <div class="form-row" v-if="newDevice.deviceCategory === '手机' || newDevice.deviceCategory === '平板'">
                  <div class="form-group half">
                    <label>RAM（运行内存）</label>
                    <select v-model="newDevice.ram">
                      <option value="">不选择</option>
                      <option v-for="r in ramOptions" :key="r" :value="r + 'GB'">{{ r }}GB</option>
                    </select>
                  </div>
                  <div class="form-group half">
                    <label>ROM（存储空间）</label>
                    <select v-model="newDevice.rom">
                      <option value="">不选择</option>
                      <option v-for="r in romOptions" :key="r" :value="r">{{ r }}</option>
                    </select>
                  </div>
                </div>
                <div class="add-device-error" v-if="addDeviceError">{{ addDeviceError }}</div>
                <button class="add-device-submit" @click="submitAddDevice" :disabled="addDeviceSubmitting">
                  {{ addDeviceSubmitting ? '添加中...' : '确认添加' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showDevicePrefModal" class="modal-overlay" @click="showDevicePrefModal = false">
      <div class="pref-modal-content" @click.stop>
        <div class="modal-header">
          <h2>个性化选机</h2>
          <button class="modal-close" @click="showDevicePrefModal = false">×</button>
        </div>
        <div class="pref-form">
          <div class="pref-section">
            <label class="pref-label">产品类型</label>
            <div class="tag-group">
              <span v-for="cat in productCategories" :key="cat" :class="['tag', { active: devicePref.category === cat }]" @click="selectCategory(cat)">{{ cat }}</span>
            </div>
          </div>
          <div v-if="devicePref.category" class="pref-section">
            <label class="pref-label">品牌</label>
            <div class="tag-group">
              <span v-for="brand in currentBrands" :key="brand" :class="['tag', { active: devicePref.brand === brand }]" @click="devicePref.brand = brand">{{ brand }}</span>
            </div>
          </div>
          <div class="pref-section">
            <label class="pref-label">关注关键词</label>
            <div class="tag-group">
              <span v-for="kw in currentKeywords" :key="kw" :class="['tag', { active: devicePref.keywords.includes(kw) }]" @click="toggleKeyword(kw)">{{ kw }}</span>
            </div>
          </div>
          <div class="pref-section">
            <label class="pref-label">价格区间</label>
            <div class="price-input-row">
              <input type="number" class="price-input" v-model.number="priceRange[0]" min="0" max="50000" step="100" placeholder="最低价" @change="syncRange" />
              <span class="price-separator">—</span>
              <input type="number" class="price-input" v-model.number="priceRange[1]" min="0" max="50000" step="100" placeholder="最高价" @change="syncRange" />
            </div>
            <div class="range-slider">
              <div class="range-track"></div>
              <div class="range-fill" :style="{ left: (priceRange[0] / 50000 * 100) + '%', right: (100 - priceRange[1] / 50000 * 100) + '%' }"></div>
              <input type="range" min="0" max="50000" step="100" v-model.number="priceRange[0]" @input="syncRange">
              <input type="range" min="0" max="50000" step="100" v-model.number="priceRange[1]" @input="syncRange">
            </div>
            <div class="range-labels">
              <span>¥0</span>
              <span>¥10000</span>
              <span>¥20000</span>
              <span>¥30000</span>
              <span>¥40000</span>
              <span>¥50000</span>
            </div>
          </div>
          <button class="pref-submit" @click="submitDevicePref" :disabled="!devicePref.category">生成推荐需求</button>
        </div>
      </div>
    </div>

    <div v-if="showConfirmModal" class="modal-overlay" @click="closeConfirmModal">
      <div class="confirm-modal-content" @click.stop>
        <div class="modal-header">
          <h2>确认操作</h2>
          <button class="modal-close" @click="closeConfirmModal">×</button>
        </div>
        <div class="confirm-message">{{ confirmMessage }}</div>
        <div class="confirm-actions">
          <button class="confirm-cancel-btn" @click="closeConfirmModal">取消</button>
          <button class="confirm-ok-btn" @click="executeConfirmedAction">确定</button>
        </div>
      </div>
    </div>

    <div v-if="showAuthModal" class="modal-overlay" @click="closeAuthModal">
      <div class="auth-modal-content" @click.stop>
        <div class="modal-header">
          <h2>{{ authMode === 'login' ? '登录' : '注册' }}</h2>
          <button class="modal-close" @click="closeAuthModal">×</button>
        </div>
        <form class="auth-form" @submit.prevent="handleAuth">
          <div v-if="authMode === 'login'" class="form-group">
            <label>邮箱/手机号</label>
            <input type="text" v-model="authForm.email" placeholder="请输入邮箱或手机号" required>
          </div>
          <div v-if="authMode === 'register'" class="form-group">
            <label>邮箱</label>
            <input type="email" v-model="authForm.email" placeholder="请输入邮箱">
          </div>
          <div v-if="authMode === 'register'" class="form-group">
            <label>手机号</label>
            <input type="text" v-model="authForm.phone" placeholder="请输入手机号">
          </div>
          <div v-if="authMode === 'register'" class="form-hint">
            邮箱和手机号至少填写一个
          </div>
          <div class="form-group">
            <label>密码</label>
            <input type="password" v-model="authForm.password" placeholder="请输入密码" required>
          </div>
          <div v-if="authMode === 'register'" class="form-group">
            <label>昵称</label>
            <input type="text" v-model="authForm.nickname" placeholder="请输入昵称" required>
          </div>
          <button type="submit" class="auth-submit" :disabled="authLoading">
            {{ authLoading ? '处理中...' : (authMode === 'login' ? '登录' : '注册') }}
          </button>
          <div v-if="authError" class="error-message">{{ authError }}</div>
          <div class="auth-switch">
            {{ authMode === 'login' ? '没有账号？' : '已有账号？' }}
            <span class="auth-link" @click="authMode = authMode === 'login' ? 'register' : 'login'">
              {{ authMode === 'login' ? '立即注册' : '立即登录' }}
            </span>
          </div>
        </form>
      </div>
    </div>

    <div v-if="showSettingsModal" class="modal-overlay" @click="closeSettingsModal">
      <div class="settings-modal-content" @click.stop>
        <button class="settings-close-btn" @click="closeSettingsModal">×</button>
        <div class="settings-left-nav">
          <div class="settings-nav-header">设置</div>
          <div :class="['settings-nav-item', { active: settingsTab === 'profile' }]" @click="settingsTab = 'profile'">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
            个人中心
          </div>
          <div :class="['settings-nav-item', { active: settingsTab === 'security' }]" @click="settingsTab = 'security'">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
            账号安全
          </div>
        </div>

        <div class="settings-right-panel">
          <h2 class="settings-panel-title">{{ settingsTab === 'profile' ? '个人中心' : '账号安全' }}</h2>

          <div v-if="settingsTab === 'profile'" class="settings-panel-body">
            <div class="avatar-upload-area">
              <div class="avatar-preview" @click="triggerAvatarUpload">
                <img v-if="profileForm.avatarPreview" :src="profileForm.avatarPreview" class="profile-avatar-img" />
                <div v-else class="profile-avatar-placeholder">{{ currentUser.nickname?.charAt(0) }}</div>
                <div class="avatar-upload-overlay">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
                    <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path>
                    <circle cx="12" cy="13" r="4"></circle>
                  </svg>
                </div>
              </div>
              <input type="file" ref="avatarInput" accept="image/*" @change="handleAvatarFileChange" style="display:none" />
              <span class="avatar-hint">点击更换头像</span>
            </div>
            <form class="settings-form" @submit.prevent="saveProfile">
              <div class="form-group">
                <label>昵称</label>
                <input type="text" v-model="profileForm.nickname" placeholder="请输入昵称" required>
              </div>
              <div class="form-group">
                <label>邮箱</label>
                <input type="email" v-model="profileForm.email" placeholder="请输入邮箱">
              </div>
              <div class="form-group">
                <label>手机号</label>
                <input type="text" v-model="profileForm.phone" placeholder="请输入手机号">
              </div>
              <div class="form-group">
                <label>性别</label>
                <select v-model="profileForm.gender">
                  <option value="">请选择</option>
                  <option value="男">男</option>
                  <option value="女">女</option>
                  <option value="其他">其他</option>
                </select>
              </div>
              <div class="form-group">
                <label>生日</label>
                <input type="date" v-model="profileForm.birthday">
              </div>
              <div class="settings-form-actions">
                <button type="submit" class="settings-save-btn" :disabled="profileLoading">
                  {{ profileLoading ? '保存中...' : '保存修改' }}
                </button>
              </div>
              <div v-if="profileError" class="error-message">{{ profileError }}</div>
              <div v-if="profileSuccess" class="success-message">{{ profileSuccess }}</div>
            </form>

            <div class="preference-section">
              <h3>设备偏好（用于智能推荐）</h3>
              <p class="preference-hint">以下偏好会根据您收藏的设备自动生成。您也可以手动修改。</p>

              <div v-if="autoPreference.autoBrand" class="auto-preference-badge">
                根据您的收藏自动推荐：偏好品牌 <strong>{{ autoPreference.autoBrand }}</strong>
                <template v-if="autoPreference.autoBudgetMin !== ''">，价格区间 <strong>{{ autoPreference.autoBudgetMin }} - {{ autoPreference.autoBudgetMax }} 元</strong></template>
              </div>

              <form class="settings-form" @submit.prevent="savePreference">
                <div class="form-group">
                  <label>偏好品牌</label>
                  <input type="text" v-model="preferenceForm.preferredBrands" placeholder="例如：苹果、华为、小米（多个品牌用空格或逗号分隔）" />
                </div>
                <div class="form-row">
                  <div class="form-group">
                    <label>最低预算（元）</label>
                    <input type="number" v-model.number="preferenceForm.budgetMin" min="0" placeholder="例如：2000" />
                  </div>
                  <div class="form-group">
                    <label>最高预算（元）</label>
                    <input type="number" v-model.number="preferenceForm.budgetMax" min="0" placeholder="例如：8000" />
                  </div>
                </div>
                <div class="form-group">
                  <label>主要用途</label>
                  <select v-model="preferenceForm.primaryUse">
                    <option value="">请选择</option>
                    <option value="日常使用">日常使用</option>
                    <option value="游戏娱乐">游戏娱乐</option>
                    <option value="拍照摄影">拍照摄影</option>
                    <option value="商务办公">商务办公</option>
                    <option value="学习阅读">学习阅读</option>
                    <option value="运动户外">运动户外</option>
                  </select>
                </div>
                <div class="settings-form-actions">
                  <button type="submit" class="settings-save-btn" :disabled="preferenceLoading">
                    {{ preferenceLoading ? '保存中...' : '保存偏好' }}
                  </button>
                </div>
                <div v-if="preferenceError" class="error-message">{{ preferenceError }}</div>
                <div v-if="preferenceSuccess" class="success-message">{{ preferenceSuccess }}</div>
              </form>
            </div>
          </div>

          <div v-if="settingsTab === 'security'" class="settings-panel-body">
            <div class="security-section">
              <h3>修改密码</h3>
              <form class="settings-form" @submit.prevent="changePassword">
                <div class="form-group">
                  <label>旧密码</label>
                  <input type="password" v-model="passwordForm.oldPassword" placeholder="请输入旧密码" required>
                </div>
                <div class="form-group">
                  <label>新密码</label>
                  <input type="password" v-model="passwordForm.newPassword" placeholder="请输入新密码（至少6位）" required>
                </div>
                <div class="form-group">
                  <label>确认新密码</label>
                  <input type="password" v-model="passwordForm.confirmPassword" placeholder="请再次输入新密码" required>
                </div>
                <div class="settings-form-actions">
                  <button type="submit" class="settings-save-btn" :disabled="passwordLoading">
                    {{ passwordLoading ? '修改中...' : '修改密码' }}
                  </button>
                </div>
                <div v-if="passwordError" class="error-message">{{ passwordError }}</div>
                <div v-if="passwordSuccess" class="success-message">{{ passwordSuccess }}</div>
              </form>
            </div>
            <div class="security-section">
              <h3>注销账号</h3>
              <template v-if="!showDeleteConfirm">
                <p class="danger-hint">注销后，您的所有数据（包括聊天记录、收藏设备等）将被永久删除，无法恢复。</p>
                <div class="settings-form-actions">
                  <button type="button" class="delete-account-btn" @click="showDeleteConfirm = true">注销账号</button>
                </div>
              </template>
              <template v-else>
                <div class="danger-section">
                  <p class="danger-hint">注销后，您的所有数据（包括聊天记录、收藏设备等）将被永久删除，无法恢复。请输入密码确认此操作。</p>
                  <form class="settings-form" @submit.prevent="deleteAccount">
                    <div class="form-group">
                      <label>请输入密码确认</label>
                      <input type="password" v-model="deleteForm.password" placeholder="请输入当前密码" required>
                    </div>
                    <div class="settings-form-actions">
                      <button type="button" class="cancel-delete-btn" @click="showDeleteConfirm = false; deleteForm.password = ''">取消</button>
                      <button type="submit" class="delete-account-btn" :disabled="deleteLoading">
                        {{ deleteLoading ? '注销中...' : '确认注销' }}
                      </button>
                    </div>
                    <div v-if="deleteError" class="error-message">{{ deleteError }}</div>
                  </form>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, computed } from 'vue'
import { marked } from 'marked'

const messagesContainer = ref(null)
const inputMessage = ref('')
const messages = ref([])
const sending = ref(false)
const loading = ref(false)
const deviceId = ref('')
const sessionId = ref('')
const imageBase64 = ref('')
const devices = ref([])
const chatHistory = ref([])
const currentChatIndex = ref(-1)
const apiConnected = ref(false)
const apiChecking = ref(true)
const currentNav = ref('chat')
const showHistoryOverlay = ref(false)

const favoritesByCategory = ref({})
const favoriteTotalCount = ref(0)
const favoritesLoading = ref(false)
const unreadFavoritesCount = ref(0)

const loadFavorites = async () => {
  try {
    favoritesLoading.value = true
    const response = await fetch('/api/favorites/list', {
      headers: { 
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      }
    })
    const data = await response.json()
    if (data.success) {
      favoritesByCategory.value = data.favorites
      favoriteTotalCount.value = data.total
      // Load unread count from localStorage
      const storedUnread = localStorage.getItem('unreadFavoritesCount')
      unreadFavoritesCount.value = storedUnread ? parseInt(storedUnread, 10) : 0
    }
  } catch (error) {
    console.error('加载收藏失败:', error)
  } finally {
    favoritesLoading.value = false
  }
}

const addFavorite = async (deviceName, deviceCategory, extra = {}) => {
  try {
    const response = await fetch('/api/favorites/add', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: JSON.stringify({
        deviceName,
        deviceCategory,
        brand: extra.brand || '',
        price: extra.price || '',
        specs: extra.specs || '',
        imageUrl: extra.imageUrl || '',
        jdUrl: extra.jdUrl || ''
      })
    })
    const data = await response.json()
    if (data.success) {
      unreadFavoritesCount.value++
      localStorage.setItem('unreadFavoritesCount', unreadFavoritesCount.value.toString())
      await loadFavorites()
    }
    return data
  } catch (error) {
    console.error('收藏失败:', error)
    return { success: false, message: '收藏失败' }
  }
}

const removeFavorite = async (deviceName) => {
  try {
    const response = await fetch(`/api/favorites/remove?deviceName=${encodeURIComponent(deviceName)}`, {
      method: 'DELETE',
      headers: { 
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      }
    })
    const data = await response.json()
    if (data.success) {
      await loadFavorites()
    }
  } catch (error) {
    console.error('取消收藏失败:', error)
  }
}

const isFavorited = (deviceName) => {
  for (const devices of Object.values(favoritesByCategory.value)) {
    if (devices.some(d => d.deviceName === deviceName)) return true
  }
  return false
}

const toggleFavorite = async (deviceName, deviceCategory, deviceInfo = {}) => {
  if (isFavorited(deviceName)) {
    await removeFavorite(deviceName)
  } else {
    await addFavorite(deviceName, deviceCategory || '手机', {
      brand: deviceInfo.brand || '',
      price: deviceInfo.price || '',
      specs: ''
    })
  }
}

const toggleFavoriteMsg = async (msg) => {
  await addFavorite(msg.deviceName, msg.deviceCategory || '手机', {
    brand: msg.deviceBrand || '',
    price: msg.devicePrice || '',
    specs: msg.deviceSpecs || ''
  })
}

const openFavoritesPage = () => {
  if (!requireAuth()) return
  showAddDeviceModal.value = false
  showHistoryOverlay.value = false
  currentNav.value = 'favorite'
  unreadFavoritesCount.value = 0
  localStorage.setItem('unreadFavoritesCount', '0')
  loadFavorites()
}

const openMyDevice = () => {
  if (!requireAuth()) return
  showAddDeviceModal.value = false
  showHistoryOverlay.value = false
  currentNav.value = 'mydevice'
  selectedDeviceId.value = null
  loadMyDevices()
  loadWarrantyNearby()
}

const myDevices = ref([])
const mydeviceSidebarOpen = ref(false)
const mydeviceSearchText = ref('')
const selectedDeviceId = ref(null)
const selectedDevice = ref(null)
const showAddDeviceModal = ref(false)
const addDeviceSubmitting = ref(false)
const addDeviceError = ref('')
const warrantyNearbyDevices = ref([])
const showWarrantyBanner = ref(true)

const ramOptions = [2, 4, 8, 12, 16, 24, 32]
const romOptions = ['32GB', '64GB', '128GB', '256GB', '512GB', '1TB', '2TB']

const newDevice = ref({
  deviceName: '',
  snCode: '',
  activationDate: '',
  brand: '',
  deviceCategory: '',
  ram: '',
  rom: ''
})

const loadMyDevices = async (keyword = '') => {
  try {
    let url = '/api/my-device/list'
    if (keyword) {
      url += '?keyword=' + encodeURIComponent(keyword)
    }
    const response = await fetch(url, {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    const data = await response.json()
    if (data.success) {
      myDevices.value = data.devices
    }
  } catch (error) {
    console.error('加载设备列表失败:', error)
  }
}

const loadWarrantyNearby = async () => {
  try {
    const response = await fetch('/api/my-device/warranty-nearby', {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    const data = await response.json()
    if (data.success) {
      warrantyNearbyDevices.value = data.devices
    }
  } catch (error) {
    console.error('加载保修提醒失败:', error)
  }
}

let mydeviceSearchTimer = null

const onMyDeviceSearch = () => {
  if (mydeviceSearchTimer) clearTimeout(mydeviceSearchTimer)
  mydeviceSearchTimer = setTimeout(() => {
    loadMyDevices(mydeviceSearchText.value)
  }, 300)
}

const openAddDeviceModal = (prefill = {}) => {
  newDevice.value = {
    deviceName: prefill.deviceName || '',
    snCode: prefill.snCode || '',
    activationDate: prefill.activationDate || '',
    brand: prefill.brand || '',
    deviceCategory: prefill.deviceCategory || '',
    ram: prefill.ram || '',
    rom: prefill.rom || ''
  }
  addDeviceError.value = ''
  showAddDeviceModal.value = true
}

const openAddDeviceModalWithOcr = (ocrData) => {
  openAddDeviceModal({
    deviceName: ocrData.deviceName || '',
    snCode: ocrData.snCode || '',
    brand: ocrData.brand || '',
    deviceCategory: ocrData.deviceCategory || '',
    ram: ocrData.ram || '',
    rom: ocrData.rom || ''
  })
}

const navigateToMyDeviceAndDetail = async (deviceId) => {
  currentNav.value = 'mydevice'
  selectedDeviceId.value = null
  await loadMyDevices()
  await loadWarrantyNearby()
  await viewDeviceDetail(deviceId)
}

const submitAddDevice = async () => {
  addDeviceError.value = ''
  if (!newDevice.value.deviceName.trim()) {
    addDeviceError.value = '请输入设备名称'
    return
  }
  if (!newDevice.value.snCode.trim()) {
    addDeviceError.value = '请输入SN码'
    return
  }
  if (!newDevice.value.activationDate) {
    addDeviceError.value = '请选择激活日期'
    return
  }

  addDeviceSubmitting.value = true
  try {
    const response = await fetch('/api/my-device/add', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: JSON.stringify({
        deviceName: newDevice.value.deviceName.trim(),
        snCode: newDevice.value.snCode.trim(),
        activationDate: newDevice.value.activationDate,
        brand: newDevice.value.brand.trim(),
        deviceCategory: newDevice.value.deviceCategory,
        ram: newDevice.value.ram,
        rom: newDevice.value.rom
      })
    })
    const data = await response.json()
    if (data.success) {
      showAddDeviceModal.value = false
      await loadMyDevices()
    } else {
      addDeviceError.value = data.message || '添加失败'
    }
  } catch (error) {
    addDeviceError.value = '网络错误，请重试'
  } finally {
    addDeviceSubmitting.value = false
  }
}

const viewDeviceDetail = async (id) => {
  selectedDeviceId.value = id
  selectedDevice.value = null
  try {
    const response = await fetch(`/api/my-device/detail?id=${id}`, {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    const data = await response.json()
    if (data.success) {
      selectedDevice.value = data.device
    }
  } catch (error) {
    console.error('获取设备详情失败:', error)
  }
}

const confirmDeleteDevice = async (id) => {
  if (!confirm('确定要删除该设备吗？此操作不可恢复。')) return
  try {
    const response = await fetch(`/api/my-device/delete?id=${id}`, {
      method: 'DELETE',
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    const data = await response.json()
    if (data.success) {
      selectedDeviceId.value = null
      selectedDevice.value = null
      await loadMyDevices()
    }
  } catch (error) {
    console.error('删除设备失败:', error)
  }
}

const switchToChat = () => {
  showHistoryOverlay.value = false
  showAddDeviceModal.value = false
  currentNav.value = 'chat'
  // 加载保修提醒
  loadWarrantyNearby()
  nextTick(() => {
    setTimeout(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    }, 100)
  })
}

const getCategoryIcon = (category) => {
  const icons = { '手机': '📱', '平板': '📋', '手表': '⌚', '手表/手环': '⌚', '耳机': '🎧' }
  return icons[category] || '📦'
}

const isWarrantyExpired = (dev) => {
  if (!dev.warrantyExpireDate) return false
  const expireDate = new Date(dev.warrantyExpireDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return expireDate < today
}

const formatFavoriteTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const handleImageError = (e) => {
  e.target.style.display = 'none'
}

const showDevicePrefModal = ref(false)
const showConfirmModal = ref(false)
const confirmMessage = ref('')
const pendingAction = ref(null)

const parseActionRequired = (content) => {
  if (!content || !content.includes('[ACTION_REQUIRED]')) {
    return null
  }
  try {
    const jsonStr = content.replace('[ACTION_REQUIRED]', '').trim()
    return JSON.parse(jsonStr)
  } catch (e) {
    console.error('解析ACTION_REQUIRED失败:', e)
    return null
  }
}

const handleActionRequired = async (actionData) => {
  if (!actionData) return
  
  // 先处理页面跳转（如果有），再弹确认框
  const { action, deviceName, redirectPage } = actionData
  let redirectDone = false
  
  if (redirectPage) {
    if (redirectPage === 'favorite') {
      currentNav.value = 'favorite'
      showHistoryOverlay.value = false
      await loadFavorites()
    } else if (redirectPage === 'mydevice') {
      currentNav.value = 'mydevice'
      showHistoryOverlay.value = false
      await loadMyDevices()
      loadWarrantyNearby()
    }
    redirectDone = true
  }
  
  // 对于mydevice_add，跳转后预填设备名并打开弹窗（不需要二次确认）
  if (action === 'mydevice_add') {
    openAddDeviceModal({ deviceName: deviceName || '' })
    return
  }
  
  // 对于需要二次确认的操作，跳转后弹窗
  if (actionData.confirmMessage && actionData.confirmMessage.trim()) {
    confirmMessage.value = actionData.confirmMessage
    pendingAction.value = { ...actionData, redirectDone }
    showConfirmModal.value = true
  } else {
    // 不需要确认的操作，跳转后直接执行
    await executeAction({ ...actionData, redirectDone })
  }
}

const closeConfirmModal = () => {
  showConfirmModal.value = false
  confirmMessage.value = ''
  pendingAction.value = null
}

const deleteMyDevice = async (id) => {
  try {
    const response = await fetch(`/api/my-device/delete?id=${id}`, {
      method: 'DELETE',
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    return await response.json()
  } catch (error) {
    console.error('删除设备失败:', error)
    return { success: false }
  }
}

const executeConfirmedAction = async () => {
  if (!pendingAction.value) return
  
  const actionData = pendingAction.value
  closeConfirmModal()
  
  await executeAction(actionData)
}

const executeAction = async (actionData) => {
  const { action, deviceName, redirectPage, redirectDone } = actionData
  
  if (redirectPage && !redirectDone) {
    if (redirectPage === 'favorite') {
      currentNav.value = 'favorite'
      showHistoryOverlay.value = false
      await loadFavorites()
    } else if (redirectPage === 'mydevice') {
      currentNav.value = 'mydevice'
      showHistoryOverlay.value = false
      await loadMyDevices()
      loadWarrantyNearby()
    }
  }
  
  if (action === 'favorite_delete' && deviceName) {
    await removeFavorite(deviceName)
    await loadFavorites()
  } else if (action === 'favorite_add' && deviceName) {
    await addFavorite(deviceName, '手机')
    await loadFavorites()
  } else if (action === 'mydevice_delete' && deviceName) {
    const normalizeName = (s) => (s || '').toLowerCase().replace(/\s+/g, '')
    const normalizedInput = normalizeName(deviceName)
    const device = myDevices.value.find(d => normalizeName(d.deviceName) === normalizedInput)
    if (device) {
      await deleteMyDevice(device.id)
      await loadMyDevices()
    } else {
      // 尝试等用户切换页面后设备列表加载完成再查找
      await new Promise(r => setTimeout(r, 500))
      const retryDevice = myDevices.value.find(d => normalizeName(d.deviceName) === normalizedInput)
      if (retryDevice) {
        await deleteMyDevice(retryDevice.id)
        await loadMyDevices()
      }
    }
  } else if (action === 'mydevice_edit' && deviceName) {
    const device = myDevices.value.find(d => d.deviceName === deviceName)
    if (device) {
      selectedDeviceId.value = device.id
    }
  } else if (action === 'mydevice_detail' && deviceName) {
    const device = myDevices.value.find(d => d.deviceName === deviceName)
    if (device) {
      selectedDeviceId.value = device.id
    }
  }
}

const devicePref = ref({ category: '', brand: '', keywords: [] })
const priceRange = ref([2000, 8000])

const productCategories = ['手机', '平板', '手表/手环', '耳机']

const brandMap = {
  '手机': ['苹果', '三星', '华为', '小米', 'Redmi', 'OPPO', 'vivo', 'iQOO', '荣耀', '一加', 'realme', '魅族', '中兴', '联想', '其他'],
  '平板': ['苹果', '三星', '华为', '小米', '联想', 'OPPO', 'vivo', '荣耀', '微软', '其他'],
  '手表/手环': ['苹果', '华为', '小米', '三星', 'OPPO', 'vivo', '荣耀', '佳明', 'Fitbit', '其他'],
  '耳机': ['苹果', '华为', '小米', '三星', '索尼', 'OPPO', 'vivo', '荣耀', 'Bose', 'JBL', '森海塞尔', 'AKG', '其他']
}

const keywordMap = {
  '手机': ['性能', '影像', '续航', '屏幕', '游戏', '轻薄', '性价比', '防水', '快充', '外观'],
  '平板': ['性能', '屏幕', '续航', '轻薄', '性价比', '手写笔', '生产力'],
  '手表/手环': ['续航', '防水', '运动监测', '健康监测', '外观', '性价比', '智能功能'],
  '耳机': ['音质', '降噪', '续航', '舒适度', '防水', '性价比', '延迟', '外观']
}

const currentBrands = computed(() => {
  return devicePref.value.category ? brandMap[devicePref.value.category] || [] : []
})

const currentKeywords = computed(() => {
  return devicePref.value.category ? keywordMap[devicePref.value.category] || [] : []
})

const selectCategory = (cat) => {
  devicePref.value.category = cat
  devicePref.value.brand = ''
}

const toggleKeyword = (kw) => {
  const idx = devicePref.value.keywords.indexOf(kw)
  if (idx > -1) {
    devicePref.value.keywords.splice(idx, 1)
  } else {
    devicePref.value.keywords.push(kw)
  }
}

const syncRange = () => {
  if (priceRange.value[0] > priceRange.value[1]) {
    priceRange.value = [priceRange.value[1], priceRange.value[0]]
  }
}

const openDevicePrefModal = () => {
  if (!requireAuth()) return
  showDevicePrefModal.value = true
}

const isLoggedIn = ref(false)
const showAuthModal = ref(false)
const authMode = ref('login')
const authLoading = ref(false)
const authError = ref('')
const showUserMenu = ref(false)
const showSettingsModal = ref(false)
const settingsTab = ref('profile')
const profileLoading = ref(false)
const profileError = ref('')
const profileSuccess = ref('')
const preferenceLoading = ref(false)
const preferenceError = ref('')
const preferenceSuccess = ref('')
const preferenceForm = ref({
  preferredBrands: '',
  budgetMin: '',
  budgetMax: '',
  primaryUse: ''
})
const autoPreference = ref({
  autoBrand: '',
  autoBudgetMin: '',
  autoBudgetMax: ''
})
const avatarInput = ref(null)

const currentUser = ref({
  id: null,
  email: '',
  phone: '',
  nickname: '',
  gender: '',
  birthday: '',
  avatarData: ''
})

const displayNickname = computed(() => {
  const name = currentUser.value.nickname || ''
  if (name.length > 4) {
    return name.substring(0, 4) + '...'
  }
  return name
})

const authForm = ref({
  email: '',
  phone: '',
  password: '',
  nickname: ''
})

const profileForm = ref({
  nickname: '',
  email: '',
  phone: '',
  gender: '',
  birthday: '',
  avatarPreview: ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordLoading = ref(false)
const passwordError = ref('')
const passwordSuccess = ref('')

const deleteForm = ref({
  password: ''
})
const deleteLoading = ref(false)
const deleteError = ref('')
const showDeleteConfirm = ref(false)

const apiStatusClass = computed(() => {
  if (apiChecking.value) return 'checking'
  return apiConnected.value ? 'connected' : 'disconnected'
})

const apiStatusText = computed(() => {
  if (apiChecking.value) return '检测中...'
  return apiConnected.value ? 'API 已连接' : 'API 未连接'
})

const generateId = () => 'id_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)

const formatMessageTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const formatHistoryTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  if (isToday) {
    return `${hours}:${minutes}`
  } else {
    return `${month}-${day} ${hours}:${minutes}`
  }
}

const checkApiConnection = async () => {
  try {
    const response = await fetch('/api/chat/status')
    const data = await response.json()
    apiConnected.value = data.apiEnabled !== false
  } catch (error) {
    apiConnected.value = false
  } finally {
    apiChecking.value = false
  }
}

const initIds = () => {
  let id = localStorage.getItem('mdm_device_id')
  if (!id) { id = generateId(); localStorage.setItem('mdm_device_id', id) }
  deviceId.value = id
  chatHistory.value = []
  createNewChat()
  checkApiConnection()
}

const loadChatHistory = () => {
  const history = localStorage.getItem('mdm_chat_history')
  if (history) {
    try {
      chatHistory.value = JSON.parse(history)
    } catch (e) {
      chatHistory.value = []
    }
  }
}

const saveChatHistory = () => {
  localStorage.setItem('mdm_chat_history', JSON.stringify(chatHistory.value))
}

const loadChatHistoryFromBackend = async () => {
  if (!isLoggedIn.value) return
  try {
    const response = await fetch('/api/chat-history/list', {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    const data = await response.json()
    if (data.success && data.histories && data.histories.length > 0) {
      const loadedHistory = data.histories.map(h => ({
        sessionId: h.sessionId,
        title: h.title || '新对话',
        messages: [],
        messagesLoaded: false,
        timestamp: h.updatedAt ? new Date(h.updatedAt).getTime() : Date.now()
      }))
      chatHistory.value = loadedHistory
      return loadedHistory
    }
  } catch (error) {
    console.error('加载后端历史对话失败:', error)
  }
  return []
}

const loadChatMessages = async (sessionId) => {
  if (!isLoggedIn.value) return null
  try {
    const response = await fetch(`/api/chat-history/detail?sessionId=${encodeURIComponent(sessionId)}`, {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    const data = await response.json()
    if (data.success && data.messages) {
      return data.messages
    }
  } catch (error) {
    console.error('加载对话详情失败:', error)
  }
  return null
}

const saveChatHistoryToBackend = async (chat) => {
  if (!isLoggedIn.value) return
  try {
    await fetch('/api/chat-history/save', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: new URLSearchParams({
        sessionId: chat.sessionId,
        title: chat.title || '新对话',
        messagesJson: JSON.stringify(chat.messages || [])
      })
    })
  } catch (error) {
    console.error('保存对话到后端失败:', error)
  }
}

const deleteChatHistoryFromBackend = async (sessionId) => {
  if (!isLoggedIn.value) return
  try {
    await fetch(`/api/chat-history/delete?sessionId=${encodeURIComponent(sessionId)}`, {
      method: 'DELETE',
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
  } catch (error) {
    console.error('删除后端对话失败:', error)
  }
}

const requireAuth = (action) => {
  if (!isLoggedIn.value) {
    openAuthModal()
    return false
  }
  return true
}

const createNewChat = () => {
  const newSessionId = generateId()
  const newChat = {
    sessionId: newSessionId,
    title: '新对话',
    messages: [],
    messagesLoaded: true,
    timestamp: Date.now()
  }
  chatHistory.value.unshift(newChat)
  saveChatHistory()
  sessionId.value = newSessionId
  messages.value = []
  currentChatIndex.value = 0
  addWelcomeMessage()
}

const deleteChat = (index) => {
  if (chatHistory.value.length <= 1) {
    alert('至少需要保留一个对话!')
    return
  }
  if (!confirm('确定要删除这个对话吗?')) {
    return
  }
  const sessionIdToDelete = chatHistory.value[index].sessionId
  chatHistory.value.splice(index, 1)
  saveChatHistory()
  deleteChatHistoryFromBackend(sessionIdToDelete)
  if (currentChatIndex.value === index) {
    switchChat(0)
  } else if (currentChatIndex.value > index) {
    currentChatIndex.value--
  }
}

const switchChat = async (index) => {
  if (index < 0 || index >= chatHistory.value.length) return
  currentChatIndex.value = index
  const chat = chatHistory.value[index]
  sessionId.value = chat.sessionId
  
  if (!chat.messagesLoaded) {
    const loadedMessages = await loadChatMessages(chat.sessionId)
    if (loadedMessages) {
      chat.messages = loadedMessages
      chat.messagesLoaded = true
    }
  }
  
  messages.value = [...chat.messages]
  if (messages.value.length > 0 && !messages.value[0].timestamp) {
    messages.value = messages.value.map((msg, idx) => ({
      ...msg,
      timestamp: msg.timestamp || (Date.now() - (messages.value.length - idx) * 60000)
    }))
  }
  scrollToBottom()
}

const updateCurrentChat = () => {
  if (currentChatIndex.value >= 0 && currentChatIndex.value < chatHistory.value.length) {
    chatHistory.value[currentChatIndex.value].messages = [...messages.value]
    // 有用户消息且标题为"新对话"时，取首条消息前20字作为标题
    if (messages.value.length > 0 && chatHistory.value[currentChatIndex.value].title === '新对话') {
      const firstUserMsg = messages.value.find(m => m.type === 'user')
      if (firstUserMsg) {
        chatHistory.value[currentChatIndex.value].title =
          firstUserMsg.content.substring(0, 20) + (firstUserMsg.content.length > 20 ? '...' : '')
      }
    }
    // 消息全部撤回后，标题恢复为"新对话"
    if (messages.value.length === 0 || !messages.value.find(m => m.type === 'user')) {
      chatHistory.value[currentChatIndex.value].title = '新对话'
    }
    chatHistory.value[currentChatIndex.value].updatedAt = Date.now()
    saveChatHistory()
    saveChatHistoryToBackend(chatHistory.value[currentChatIndex.value])
  }
}

const parseMarkdown = (text) => {
  marked.setOptions({
    breaks: true,
    gfm: true,
    silent: true
  })
  let html = marked.parse(text)
  html = html.replace(/\*\*(.+?)\*\*/g, '$1')
             .replace(/\*(.+?)\*/g, '$1')
             .replace(/###\s*(.+)/g, '$1')
             .replace(/##\s*(.+)/g, '$1')
             .replace(/#\s*(.+)/g, '$1')
             .replace(/^-\s*(.+)/gm, '$1')
  return html
}

const extractRecommendDevices = (content) => {
  if (!content) return []
  const devices = []
  const patterns = [
    /第一推荐[：:]?\s*([^\n]+)/,
    /第二推荐[：:]?\s*([^\n]+)/,
    /第三推荐[：:]?\s*([^\n]+)/,
    /第四推荐[：:]?\s*([^\n]+)/,
    /第五推荐[：:]?\s*([^\n]+)/,
    /推荐[：:]?\s*([^\n]+)/
  ]
  patterns.forEach((pattern, index) => {
    const match = content.match(pattern)
    if (match && match[1]) {
      let deviceName = match[1].trim()
      deviceName = deviceName.replace(/[。，,、]/g, '')
      if (deviceName.length > 2 && !devices.includes(deviceName)) {
        devices.push(deviceName)
      }
    }
  })
  return devices
}

const extractDeviceInfo = (content, deviceName) => {
  if (!content || !deviceName) return { brand: '', price: '', category: '手机' }
  
  const info = { brand: '', price: '', category: '手机' }
  
  // 判断设备类别
  const lowerDevice = deviceName.toLowerCase()
  if (lowerDevice.includes('手表') || lowerDevice.includes('watch') || lowerDevice.includes('gt')) {
    info.category = '手表/手环'
  } else if (lowerDevice.includes('耳机') || lowerDevice.includes('buds') || lowerDevice.includes('airpods') || 
             lowerDevice.includes('ear') || lowerDevice.includes('pods')) {
    info.category = '耳机'
  } else if (lowerDevice.includes('平板') || lowerDevice.includes('pad') || lowerDevice.includes('tablet')) {
    info.category = '平板'
  }
  
  // 提取品牌（从设备名称中提取）
  const brandPatterns = ['苹果', '华为', '小米', '三星', 'OPPO', 'vivo', '一加', 'realme', '荣耀', '联想', '华硕']
  for (const brand of brandPatterns) {
    if (deviceName.includes(brand)) {
      info.brand = brand
      break
    }
  }
  
  // 从回复内容中提取价格
  const pricePattern = new RegExp(deviceName.replace(/[()（）]/g, '[()（）]').replace(/\s+/g, '\\s*') + '[^\n]*?([\\d,]+\\s*元)')
  const priceMatch = content.match(pricePattern)
  if (priceMatch && priceMatch[1]) {
    info.price = priceMatch[1]
  } else {
    // 如果没有匹配到，尝试从整个回复中提取价格
    const generalPriceMatch = content.match(new RegExp('参考价格[：:]?\\s*([\\d,]+\\s*元)'))
    if (generalPriceMatch && generalPriceMatch[1]) {
      info.price = generalPriceMatch[1]
    }
  }
  
  return info
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const addWelcomeMessage = () => {
  // 不再自动添加欢迎消息，使用欢迎界面
  updateCurrentChat()
}

const recallMessage = async (index) => {
  if (!confirm('确定要撤回此消息吗？将同时回退助手的回复内容。')) return
  
  let removeCount = 1
  if (index + 1 < messages.value.length && messages.value[index + 1].type === 'ai') {
    removeCount = 2
  }
  
  messages.value.splice(index, removeCount)
  updateCurrentChat()
  scrollToBottom()
  
  // 调用后端API删除数据库中的消息
  try {
    await fetch('/api/chat/recall', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: JSON.stringify({
        sessionId: currentChatSessionId.value,
        count: removeCount
      })
    })
  } catch (error) {
    console.error('撤回消息失败:', error)
  }
}

const stopGeneration = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  loading.value = false
  // sending 会在 catch 的 finally 中重置
}

const useSuggestion = (text) => {
  inputMessage.value = text
  sendMessage()
}

let abortController = null

const sendMessage = async () => {
  const content = inputMessage.value.trim()
  if (!content || sending.value) return
  const msgImageDataUrl = imageBase64.value ? 'data:image/jpeg;base64,' + imageBase64.value : null
  messages.value.push({ type: 'user', content, imageDataUrl: msgImageDataUrl, timestamp: Date.now() })
  inputMessage.value = ''
  sending.value = true
  loading.value = true
  scrollToBottom()
  updateCurrentChat()

  // 记录发送请求时的会话ID，用于验证响应是否属于当前会话
  const requestSessionId = sessionId.value

  // 创建新的 AbortController 用于取消请求
  abortController = new AbortController()

  try {
    const response = await fetch('/api/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: JSON.stringify({
        deviceId: deviceId.value,
        sessionId: sessionId.value,
        message: content,
        imageBase64: imageBase64.value,
        imageType: 'jpeg'
      }),
      signal: abortController.signal
    })
    const data = await response.json()
    loading.value = false
    
    // 验证响应是否属于当前会话，如果用户已切换对话则丢弃响应
    if (sessionId.value !== requestSessionId) {
      console.warn('响应属于已切换的对话，丢弃:', requestSessionId)
      return
    }
    
    if (data.success) {
      const actionData = parseActionRequired(data.reply)
      if (actionData) {
        handleActionRequired(actionData)
      } else {
        messages.value.push({ type: 'ai', content: data.reply, timestamp: Date.now(), deviceName: data.deviceName, deviceCategory: data.deviceCategory, deviceBrand: data.deviceBrand, devicePrice: data.devicePrice, deviceSpecs: data.deviceSpecs, usedJdData: data.usedJdData, showFavorite: data.showFavorite })
        if (data.intent === 'OCR_SAVE_DEVICE' && data.ocrDeviceData) {
        try {
          currentNav.value = 'mydevice'
          showHistoryOverlay.value = false
          selectedDeviceId.value = null
          await loadMyDevices()
          loadWarrantyNearby()
          openAddDeviceModalWithOcr(data.ocrDeviceData)
        } catch (e) {
          console.error('切换页面失败:', e)
          openAddDeviceModalWithOcr(data.ocrDeviceData)
        }
        }
      }
    } else {
      messages.value.push({ type: 'system', content: data.reply || '抱歉，服务暂时不可用。', timestamp: Date.now() })
    }
    updateCurrentChat()
  } catch (error) {
    // 验证响应是否属于当前会话，如果用户已切换对话则丢弃错误
    if (sessionId.value !== requestSessionId) {
      console.warn('错误属于已切换的对话，丢弃:', requestSessionId)
      return
    }
    
    if (error.name === 'AbortError') {
      const lastUserIdx = messages.value.map(m => m.type).lastIndexOf('user')
      if (lastUserIdx >= 0) {
        messages.value.splice(lastUserIdx, 1)
      }
    } else {
      console.error('Error:', error)
      loading.value = false
      messages.value.push({ type: 'system', content: '网络错误，请检查连接后重试。', timestamp: Date.now() })
    }
    updateCurrentChat()
  } finally {
    sending.value = false
    imageBase64.value = ''
    scrollToBottom()
  }
}

// 语音输入
const isListening = ref(false)
let recognition = null

function initSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) {
    console.warn('浏览器不支持语音识别')
    return null
  }
  const recog = new SpeechRecognition()
  recog.lang = 'zh-CN'
  recog.continuous = true
  recog.interimResults = true
  recog.maxAlternatives = 1

  let finalTranscript = ''

  recog.onresult = (event) => {
    let interimTranscript = ''
    let newFinal = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i]
      const transcript = result[0].transcript
      if (result.isFinal) {
        newFinal += transcript
      } else {
        interimTranscript += transcript
      }
    }
    // 累积最终结果
    finalTranscript += newFinal
    // 实时显示：已确定的文字 + 当前正在说的中间结果
    inputMessage.value = finalTranscript + interimTranscript
  }

  recog.onstart = () => {
    finalTranscript = ''
  }

  recog.onend = () => {
    // 用户停止说话后自动发送
    if (isListening.value) {
      isListening.value = false
      const content = inputMessage.value.trim()
      if (content) {
        sendMessage()
      }
    }
  }

  recog.onerror = (event) => {
    console.error('语音识别错误:', event.error)
    isListening.value = false
    if (event.error === 'not-allowed') {
      alert('请允许使用麦克风权限')
    }
  }

  return recog
}

function toggleVoiceInput() {
  if (isListening.value) {
    // 停止录音
    if (recognition) {
      recognition.stop()
    }
    isListening.value = false
    // 停止时如果输入框有内容则发送
    const content = inputMessage.value.trim()
    if (content) {
      sendMessage()
    }
  } else {
    // 开始录音
    if (!recognition) {
      recognition = initSpeechRecognition()
    }
    if (recognition) {
      inputMessage.value = ''
      isListening.value = true
      recognition.start()
    } else {
      alert('您的浏览器不支持语音识别，请使用Chrome浏览器')
    }
  }
}

function previewImage(dataUrl) {
  const w = window.open('', '_blank')
  if (w) {
    w.document.write(`<img src="${dataUrl}" style="max-width:100%;max-height:100vh;display:block;margin:auto;" />`)
    w.document.title = '图片预览'
  }
}

const handleImageUpload = async (event) => {
  if (!requireAuth()) { event.target.value = ''; return }
  const file = event.target.files[0]
  if (!file) return
  const imageType = file.type.split('/')[1] || 'jpeg'
  const reader = new FileReader()
  reader.onload = (e) => {
    imageBase64.value = e.target.result.split(',')[1]
    const imageDataUrl = e.target.result
    messages.value.push({ type: 'user', content: '', imageDataUrl, timestamp: Date.now() })
    sending.value = true
    loading.value = true
    scrollToBottom()
    updateCurrentChat()

    // 记录发送请求时的会话ID，用于验证响应是否属于当前会话
    const requestSessionId = sessionId.value

    abortController = new AbortController()

    fetch('/api/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: JSON.stringify({
        deviceId: deviceId.value,
        sessionId: sessionId.value,
        message: '',
        imageBase64: imageBase64.value,
        imageType: imageType
      }),
      signal: abortController.signal
    }).then(res => res.json())
      .then(async (data) => {
        loading.value = false
        
        // 验证响应是否属于当前会话，如果用户已切换对话则丢弃响应
        if (sessionId.value !== requestSessionId) {
          console.warn('响应属于已切换的对话，丢弃:', requestSessionId)
          return
        }
        
        if (data.success) {
          messages.value.push({ type: 'ai', content: data.reply, timestamp: Date.now() })
          if (data.intent === 'OCR_SAVE_DEVICE' && data.ocrDeviceData) {
            try {
              currentNav.value = 'mydevice'
              showHistoryOverlay.value = false
              selectedDeviceId.value = null
              await loadMyDevices()
              loadWarrantyNearby()
              openAddDeviceModalWithOcr(data.ocrDeviceData)
            } catch (e) {
              console.error('切换页面失败:', e)
              openAddDeviceModalWithOcr(data.ocrDeviceData)
            }
          }
        } else {
          messages.value.push({ type: 'system', content: data.reply || '图片识别失败。', timestamp: Date.now() })
        }
        updateCurrentChat()
      })
      .catch((error) => {
        // 验证响应是否属于当前会话，如果用户已切换对话则丢弃错误
        if (sessionId.value !== requestSessionId) {
          console.warn('错误属于已切换的对话，丢弃:', requestSessionId)
          return
        }
        
        if (error.name === 'AbortError') {
          const lastUserIdx = messages.value.map(m => m.type).lastIndexOf('user')
          if (lastUserIdx >= 0) {
            messages.value.splice(lastUserIdx, 1)
          }
        } else {
          loading.value = false
          messages.value.push({ type: 'system', content: '图片上传失败。', timestamp: Date.now() })
        }
        updateCurrentChat()
      })
      .finally(() => {
        sending.value = false
        imageBase64.value = ''
        scrollToBottom()
      })
  }
  reader.readAsDataURL(file)
  event.target.value = ''
}

const toggleApiConnection = async () => {
  if (apiChecking.value) return
  apiChecking.value = true
  try {
    const response = await fetch('/api/chat/toggle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        enabled: !apiConnected.value
      })
    })
    const data = await response.json()
    apiConnected.value = data.enabled
  } catch (error) {
    console.error('切换 API 连接失败:', error)
  } finally {
    apiChecking.value = false
  }
}

const submitDevicePref = () => {
  const pref = devicePref.value
  const [priceMin, priceMax] = priceRange.value
  let text = `帮我推荐${pref.category}`
  
  if (pref.brand === '其他') {
    const mainBrands = brandMap[pref.category].filter(b => b !== '其他')
    text += `，优先推荐非主流品牌（除${mainBrands.slice(0, 5).join('、')}等之外的品牌）`
  } else if (pref.brand) {
    text += `，品牌为${pref.brand}`
  }
  
  if (pref.keywords.length > 0) text += `，重点关注${pref.keywords.join('、')}`
  text += `，价格区间在${priceMin}元到${priceMax}元之间`
  
  showDevicePrefModal.value = false
  inputMessage.value = text
  sendMessage()
}

const openAuthModal = () => {
  authMode.value = 'login'
  authForm.value = { email: '', password: '', nickname: '' }
  authError.value = ''
  showAuthModal.value = true
}

const closeAuthModal = () => {
  showAuthModal.value = false
  authError.value = ''
}

const handleAuth = async () => {
  authLoading.value = true
  authError.value = ''
  try {
    const url = authMode.value === 'login' ? '/api/user/login' : '/api/user/register'
    const formData = new FormData()
    if (authMode.value === 'login') {
      formData.append('identifier', authForm.value.email)
    } else {
      const missingFields = []
      if (!authForm.value.email && !authForm.value.phone) {
        missingFields.push('邮箱或手机号')
      }
      if (!authForm.value.nickname) {
        missingFields.push('昵称')
      }
      if (!authForm.value.password) {
        missingFields.push('密码')
      }
      if (missingFields.length > 0) {
        authError.value = '请填写以下必填项：' + missingFields.join('、')
        authLoading.value = false
        return
      }
      if (authForm.value.email) {
        formData.append('email', authForm.value.email)
      }
      if (authForm.value.phone) {
        formData.append('phone', authForm.value.phone)
      }
    }
    formData.append('password', authForm.value.password)
    if (authMode.value === 'register') {
      formData.append('nickname', authForm.value.nickname)
    }
    const response = await fetch(url, {
      method: 'POST',
      body: formData
    })
    const data = await response.json()
    if (data.success) {
      if (authMode.value === 'register') {
        authMode.value = 'login'
        authForm.value = { email: '', phone: '', password: '', nickname: '' }
        authError.value = '注册成功，请登录'
      } else {
        localStorage.setItem('mdm_token', data.token)
        localStorage.setItem('mdm_user', JSON.stringify(data.user))
        currentUser.value = data.user
        isLoggedIn.value = true
        showAuthModal.value = false
        authForm.value = { email: '', phone: '', password: '', nickname: '' }
        await loadChatHistoryFromBackend()
        // 登录后始终新建一个空白对话，不覆盖历史记录
        createNewChat()
      }
    } else {
      authError.value = data.message
    }
  } catch (error) {
    authError.value = '网络错误，请稍后重试'
  } finally {
    authLoading.value = false
  }
}

const handleLogout = () => {
  localStorage.removeItem('mdm_token')
  localStorage.removeItem('mdm_user')
  isLoggedIn.value = false
  currentUser.value = { id: null, email: '', phone: '', nickname: '', gender: '', birthday: '', avatarData: '' }
  showUserMenu.value = false
  chatHistory.value = []
  createNewChat()
}

const openSettingsModal = () => {
  profileForm.value = {
    nickname: currentUser.value.nickname || '',
    email: currentUser.value.email || '',
    phone: currentUser.value.phone || '',
    gender: currentUser.value.gender || '',
    birthday: currentUser.value.birthday || '',
    avatarPreview: currentUser.value.avatarData || ''
  }
  profileError.value = ''
  profileSuccess.value = ''
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  passwordError.value = ''
  passwordSuccess.value = ''
  deleteForm.value = { password: '' }
  deleteError.value = ''
  showDeleteConfirm.value = false
  settingsTab.value = 'profile'
  showSettingsModal.value = true
  loadPreference()
}

const closeSettingsModal = () => {
  showSettingsModal.value = false
}

const triggerAvatarUpload = () => {
  if (avatarInput.value) {
    avatarInput.value.click()
  }
}

const handleAvatarFileChange = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    profileError.value = '头像图片不能超过2MB'
    return
  }
  const reader = new FileReader()
  reader.onload = async (event) => {
    const base64 = event.target.result
    profileForm.value.avatarPreview = base64
    try {
      const formData = new FormData()
      formData.append('avatarData', base64)
      const response = await fetch('/api/user/upload-avatar', {
        method: 'POST',
        headers: {
          'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
        },
        body: formData
      })
      const data = await response.json()
      if (data.success) {
        currentUser.value = data.user
        localStorage.setItem('mdm_user', JSON.stringify(data.user))
        profileSuccess.value = '头像更新成功'
        setTimeout(() => { profileSuccess.value = '' }, 2000)
      } else {
        profileError.value = data.message
      }
    } catch (error) {
      profileError.value = '头像上传失败'
    }
  }
  reader.readAsDataURL(file)
}

const loadPreference = async () => {
  try {
    const response = await fetch('/api/user/preference', {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('mdm_token') }
    })
    const data = await response.json()
    if (data.success) {
      preferenceForm.value = {
        preferredBrands: data.preferredBrands || '',
        budgetMin: data.budgetMin === '' ? '' : data.budgetMin,
        budgetMax: data.budgetMax === '' ? '' : data.budgetMax,
        primaryUse: data.primaryUse || ''
      }
      autoPreference.value = {
        autoBrand: data.autoBrand || '',
        autoBudgetMin: data.autoBudgetMin === '' ? '' : data.autoBudgetMin,
        autoBudgetMax: data.autoBudgetMax === '' ? '' : data.autoBudgetMax
      }
    }
  } catch (error) {
    console.error('加载偏好失败:', error)
  }
}

const savePreference = async () => {
  preferenceLoading.value = true
  preferenceError.value = ''
  preferenceSuccess.value = ''
  try {
    const response = await fetch('/api/user/preference', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: JSON.stringify({
        preferredBrands: preferenceForm.value.preferredBrands,
        budgetMin: preferenceForm.value.budgetMin === '' ? null : preferenceForm.value.budgetMin,
        budgetMax: preferenceForm.value.budgetMax === '' ? null : preferenceForm.value.budgetMax,
        primaryUse: preferenceForm.value.primaryUse
      })
    })
    const data = await response.json()
    if (data.success) {
      preferenceSuccess.value = '偏好设置已保存'
      setTimeout(() => { preferenceSuccess.value = '' }, 2000)
    } else {
      preferenceError.value = data.message || '保存失败'
    }
  } catch (error) {
    preferenceError.value = '网络错误，请重试'
  } finally {
    preferenceLoading.value = false
  }
}

const saveProfile = async () => {
  profileLoading.value = true
  profileError.value = ''
  profileSuccess.value = ''
  try {
    const formData = new FormData()
    formData.append('nickname', profileForm.value.nickname)
    // 不管有没有值都发送，让后端决定是否更新/清空
    formData.append('email', profileForm.value.email || '')
    formData.append('phone', profileForm.value.phone || '')
    formData.append('gender', profileForm.value.gender || '')
    // 只在有生日值时才发送，否则不发送，后端会处理为空
    if (profileForm.value.birthday) {
      formData.append('birthday', profileForm.value.birthday)
    }
    const response = await fetch('/api/user/update-profile', {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: formData
    })
    const data = await response.json()
    if (data.success) {
      currentUser.value = data.user
      localStorage.setItem('mdm_user', JSON.stringify(data.user))
      profileSuccess.value = '个人资料更新成功'
      setTimeout(() => { profileSuccess.value = '' }, 2000)
    } else {
      profileError.value = data.message
    }
  } catch (error) {
    profileError.value = '网络错误，请稍后重试'
  } finally {
    profileLoading.value = false
  }
}

const changePassword = async () => {
  passwordLoading.value = true
  passwordError.value = ''
  passwordSuccess.value = ''
  try {
    if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
      passwordError.value = '两次输入的新密码不一致'
      passwordLoading.value = false
      return
    }
    if (passwordForm.value.newPassword.length < 6) {
      passwordError.value = '新密码长度不能少于6位'
      passwordLoading.value = false
      return
    }
    const formData = new FormData()
    formData.append('oldPassword', passwordForm.value.oldPassword)
    formData.append('newPassword', passwordForm.value.newPassword)
    const response = await fetch('/api/user/change-password', {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: formData
    })
    const data = await response.json()
    if (data.success) {
      passwordSuccess.value = '密码修改成功'
      passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
      setTimeout(() => { passwordSuccess.value = '' }, 2000)
    } else {
      passwordError.value = data.message
    }
  } catch (error) {
    passwordError.value = '网络错误，请稍后重试'
  } finally {
    passwordLoading.value = false
  }
}

const deleteAccount = async () => {
  if (!confirm('确定要注销账号吗？此操作不可撤销，所有数据将被永久删除。')) return
  deleteLoading.value = true
  deleteError.value = ''
  try {
    const formData = new FormData()
    formData.append('password', deleteForm.value.password)
    const response = await fetch('/api/user/delete-account', {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('mdm_token')
      },
      body: formData
    })
    const data = await response.json()
    if (data.success) {
      localStorage.removeItem('mdm_token')
      localStorage.removeItem('mdm_user')
      isLoggedIn.value = false
      currentUser.value = { id: null, email: '', phone: '', nickname: '', gender: '', birthday: '', avatarData: '' }
      showSettingsModal.value = false
      showDeleteConfirm.value = false
      chatHistory.value = []
      createNewChat()
      alert('账号已注销')
    } else {
      deleteError.value = data.message
    }
  } catch (error) {
    deleteError.value = '网络错误，请稍后重试'
  } finally {
    deleteLoading.value = false
  }
}

const checkLoginStatus = async () => {
  const token = localStorage.getItem('mdm_token')
  if (!token) {
    isLoggedIn.value = false
    initIds()
    return
  }

  const savedUser = localStorage.getItem('mdm_user')
  if (savedUser) {
    try {
      const user = JSON.parse(savedUser)
      currentUser.value = user
      isLoggedIn.value = true
    } catch (e) {
      console.error('解析保存的用户信息失败:', e)
    }
  }

  try {
    const response = await fetch('/api/user/info', {
      headers: {
        'Authorization': 'Bearer ' + token
      }
    })
    const data = await response.json()
    if (data.success) {
      let id = localStorage.getItem('mdm_device_id')
      if (!id) { id = generateId(); localStorage.setItem('mdm_device_id', id) }
      deviceId.value = id
      checkApiConnection()
      currentUser.value = data.user
      localStorage.setItem('mdm_user', JSON.stringify(data.user))
      isLoggedIn.value = true

      chatHistory.value = []
      const loadedHistory = await loadChatHistoryFromBackend()

      // 刷新页面时始终创建新对话，不自动切换到历史对话
      // 历史对话保留在侧边栏中，用户可手动切换查看
      createNewChat()
    } else {
      localStorage.removeItem('mdm_token')
      localStorage.removeItem('mdm_user')
      isLoggedIn.value = false
      currentUser.value = { id: null, email: '', phone: '', nickname: '', gender: '', birthday: '', avatarData: '' }
      initIds()
    }
  } catch (error) {
    console.error('验证登录状态失败，但保持登录状态:', error)
    if (!isLoggedIn.value) {
      initIds()
    }
  }
}

onMounted(() => {
  checkLoginStatus()
  loadFavorites()
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; height: 100vh; overflow: hidden; }
#app { width: 100vw; height: 100vh; display: flex; flex-direction: column; position: relative; }

.chat-app {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.main-container {
  display: flex;
  height: 100vh;
  width: 100%;
}

.sidebar {
  width: 20%;
  min-width: 160px;
  background: #e8e8e8;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  z-index: 10;
}

.sidebar-header-fixed {
  padding: 20px 16px;
  background: #d0d0d0;
  font-size: 16px;
  font-weight: bold;
  color: #333;
  border-bottom: 1px solid #ccc;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  overflow-y: auto;
}

.nav-item {
  padding: 14px 18px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 15px;
  color: #555;
  transition: all 0.2s;
  user-select: none;
}

.nav-item:hover {
  background: #d8d8d8;
}

.nav-item.active {
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  font-weight: 500;
}

.favorite-badge {
  background: #e53935;
  color: white;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  margin-left: 6px;
  vertical-align: middle;
}

.sidebar-settings {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 88px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  overflow: visible;
  z-index: 10;
  border-top: 1px solid #d0d0d0;
  background: #e8e8e8;
}

.sidebar-settings .settings-combined {
  height: auto;
  padding: 12px 16px;
  display: flex;
  align-items: center;
}

.settings-combined {
  display: flex;
  align-items: center;
  cursor: pointer;
  overflow: hidden;
  border-radius: 26px;
  height: 52px;
  transition: all 0.3s ease;
  background: transparent;
}

.settings-combined:hover {
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  box-shadow: 0 4px 12px rgba(66, 165, 245, 0.3);
}

.settings-gear {
  width: 52px;
  height: 52px;
  min-width: 52px;
  min-height: 52px;
  border-radius: 50%;
  background: #d0d0d0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555;
  transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1), background 0.3s, color 0.3s;
  z-index: 2;
  flex-shrink: 0;
}

.settings-combined:hover .settings-gear {
  transform: rotate(180deg);
  background: transparent;
  color: white;
}

.settings-pill {
  max-width: 0;
  overflow: hidden;
  white-space: nowrap;
  background: transparent;
  color: white;
  padding: 0;
  font-size: 13px;
  font-weight: 500;
  height: 52px;
  line-height: 52px;
  transition: max-width 0.4s cubic-bezier(0.4, 0, 0.2, 1), padding 0.4s cubic-bezier(0.4, 0, 0.2, 1), margin-left 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  margin-left: -10px;
  opacity: 0;
}

.settings-combined:hover .settings-pill {
  max-width: 120px;
  padding: 0 16px 0 8px;
  margin-left: 0;
  opacity: 1;
}

.settings-nickname {
  display: inline-block;
}

.favorites-page {
  width: 80%;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  overflow-y: auto;
}

.favorites-page-header {
  padding: 24px 32px;
  background: white;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  gap: 16px;
}

.favorites-page-header h2 {
  font-size: 22px;
  color: #333;
  margin: 0;
}

.favorites-count {
  font-size: 14px;
  color: #999;
}

.favorites-loading {
  text-align: center;
  padding: 60px;
  font-size: 16px;
  color: #999;
}

.empty-favorites-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: #999;
}

.empty-favorites-page svg {
  margin-bottom: 20px;
}

.empty-favorites-page p {
  font-size: 18px;
  margin: 12px 0;
}

.empty-favorites-page span {
  font-size: 14px;
}

.favorites-content {
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.favorites-category {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.favorites-category-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 2px solid #667eea;
}

.category-icon {
  font-size: 24px;
}

.category-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.category-count {
  font-size: 13px;
  color: #999;
  margin-left: auto;
}

.favorites-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.favorite-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
}

.favorite-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
}

.favorite-card-header {
  position: relative;
  height: 80px;
  background: #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.device-category-icon {
  font-size: 48px;
}

.favorite-card-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0,0,0,0.5);
  color: white;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s;
}

.favorite-card:hover .favorite-card-remove {
  opacity: 1;
}

.favorite-card-remove:hover {
  background: #e53935;
}

.favorite-card-body {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.device-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-brand {
  font-size: 13px;
  color: #666;
}

.device-price {
  font-size: 15px;
  color: #e53935;
  font-weight: 600;
}

.device-specs {
  font-size: 12px;
  color: #777;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.favorite-card-footer {
  padding: 12px 16px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.favorite-time {
  font-size: 12px;
  color: #999;
}

.jd-link {
  font-size: 12px;
  color: #42a5f5;
  text-decoration: none;
  padding: 4px 12px;
  border: 1px solid #42a5f5;
  border-radius: 12px;
  transition: all 0.2s;
}

.jd-link:hover {
  background: #42a5f5;
  color: white;
}

.mydevice-page {
  width: 80%;
  display: flex;
  flex-direction: column;
  background: #fff;
  overflow: hidden;
  position: relative;
}

.mydevice-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}



.warranty-marquee-banner {
  background: linear-gradient(135deg, #fff3cd, #ffeeba);
  border-bottom: 1px solid #ffc107;
  padding: 8px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}

.warranty-marquee-content {
  flex: 1;
  white-space: nowrap;
  animation: marquee 25s linear infinite;
}

@keyframes marquee {
  0% { transform: translateX(100%); }
  100% { transform: translateX(-100%); }
}

.marquee-title {
  font-weight: 700;
  color: #856404;
  margin-right: 15px;
}

.marquee-item {
  color: #856404;
}

.warranty-marquee-close {
  background: rgba(133, 100, 4, 0.1);
  border: none;
  color: #856404;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  flex-shrink: 0;
  margin-left: 15px;
  transition: all 0.2s;
}

.warranty-marquee-close:hover {
  background: rgba(133, 100, 4, 0.2);
}

.mydevice-sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  font-weight: 600;
  font-size: 14px;
  color: #333;
  border-bottom: 1px solid #e8e8e8;
}

.mydevice-sidebar-toggle {
  background: none;
  border: none;
  cursor: pointer;
  color: #999;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mydevice-sidebar-toggle:hover {
  background: #e0e0e0;
  color: #333;
}

.mydevice-sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.mydevice-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #555;
  font-size: 14px;
  transition: all 0.15s;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mydevice-nav-item:hover {
  background: #e8eaf0;
  color: #333;
}

.mydevice-nav-item.active {
  background: #d0d5ff;
  color: #4a5ce0;
  font-weight: 600;
}

.mydevice-nav-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.mydevice-nav-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.mydevice-sidebar-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 13px;
}

.warranty-nearby-banner {
  background: linear-gradient(135deg, #fff5f0, #fff0e6);
  border: 1px solid #ffcc80;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.warranty-nearby-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #e65100;
  margin-bottom: 12px;
}

.warranty-nearby-icon {
  font-size: 18px;
}

.warranty-nearby-count {
  background: #e65100;
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
  margin-left: auto;
}

.warranty-nearby-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.warranty-nearby-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ffe0b2;
}

.warranty-nearby-device-name {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.warranty-nearby-days {
  font-size: 13px;
  font-weight: 700;
  color: #e67e22;
  white-space: nowrap;
}

.warranty-nearby-days.urgent {
  color: #e53935;
  animation: pulse-warn 1.5s ease-in-out infinite;
}

@keyframes pulse-warn {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-group.half {
  flex: 1;
}

.mydevice-content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 24px 32px;
  min-height: 0;
}

.mydevice-top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.mydevice-expand-btn {
  background: none;
  border: 1px solid #ddd;
  border-radius: 8px;
  cursor: pointer;
  padding: 8px 10px;
  color: #666;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}

.mydevice-expand-btn:hover {
  background: #f0f0f0;
  color: #333;
  border-color: #ccc;
}

.mydevice-search-box {
  flex: 1;
  display: flex;
  align-items: center;
  background: #f5f6f8;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 12px;
  transition: border-color 0.2s;
}

.mydevice-search-box:focus-within {
  border-color: #4a5ce0;
  background: #fff;
}

.mydevice-search-icon {
  color: #999;
  flex-shrink: 0;
}

.mydevice-search-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  padding: 10px 8px;
  font-size: 14px;
  color: #333;
}

.mydevice-search-box input::placeholder {
  color: #bbb;
}

.mydevice-add-btn {
  background: #4a5ce0;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
  flex-shrink: 0;
}

.mydevice-add-btn:hover {
  background: #3a4cd0;
}

.mydevice-page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.mydevice-page-header h2 {
  font-size: 22px;
  color: #333;
  margin: 0;
}

.mydevice-count {
  font-size: 13px;
  color: #999;
}

.mydevice-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  overflow-y: auto;
}

.mydevice-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid #eee;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
  background: #fafafa;
}

.mydevice-card:hover {
  border-color: #4a5ce0;
  background: #f5f6ff;
  box-shadow: 0 2px 8px rgba(74, 92, 224, 0.1);
}

.mydevice-card-icon {
  font-size: 32px;
  flex-shrink: 0;
}

.mydevice-card-info {
  flex: 1;
  min-width: 0;
}

.mydevice-card-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.warranty-expired-badge {
  font-size: 11px;
  font-weight: 500;
  color: #e74c3c;
  background: #fdeaea;
  border: 1px solid #f5c6cb;
  padding: 2px 8px;
  border-radius: 10px;
  white-space: nowrap;
}

.mydevice-card-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 2px;
}

.mydevice-card-brand {
  font-size: 12px;
  color: #4a5ce0;
  background: #eef0ff;
  padding: 2px 8px;
  border-radius: 4px;
}

.mydevice-card-storage {
  font-size: 12px;
  color: #e67e22;
  background: #fef5e7;
  padding: 2px 8px;
  border-radius: 4px;
}

.mydevice-card-sn {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.mydevice-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
}

.mydevice-empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
  opacity: 0.4;
}

.mydevice-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.mydevice-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-shrink: 0;
}

.mydevice-back-btn {
  background: none;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 14px;
  color: #555;
  cursor: pointer;
  transition: all 0.15s;
}

.mydevice-back-btn:hover {
  background: #f5f5f5;
  border-color: #ccc;
}

.mydevice-delete-btn {
  background: none;
  border: 1px solid #ffcccc;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 14px;
  color: #e74c3c;
  cursor: pointer;
  transition: all 0.15s;
}

.mydevice-delete-btn:hover {
  background: #fff5f5;
  border-color: #e74c3c;
}

.mydevice-detail-card {
  max-width: 520px;
  text-align: center;
  padding: 32px;
  background: #fafbfc;
  border-radius: 12px;
  border: 1px solid #eee;
}

.detail-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.detail-name {
  font-size: 20px;
  font-weight: 700;
  color: #333;
  margin: 0 0 24px 0;
}

.detail-table {
  text-align: left;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-row.highlight {
  background: #fff8e1;
  border-radius: 8px;
  margin-top: 8px;
  border-bottom: none;
}

.detail-label {
  font-size: 14px;
  color: #888;
}

.detail-value {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.sn-value {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #4a5ce0;
}

.warranty-value {
  color: #e67e22;
  font-weight: 700;
  font-size: 15px;
}

.mydevice-content {
  color: #666;
  font-size: 15px;
  line-height: 1.8;
}

.add-device-modal {
  background: #fff;
  border-radius: 16px;
  padding: 28px 32px;
  width: 460px;
  max-width: 90vw;
  max-height: 85vh;
  overflow-y: auto;
}

.add-device-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.add-device-form .form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.add-device-form .form-group label {
  font-size: 14px;
  color: #555;
  font-weight: 500;
}

.add-device-form .required {
  color: #e74c3c;
}

.add-device-form .form-group input,
.add-device-form .form-group select {
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  color: #333;
  outline: none;
  transition: border-color 0.2s;
  background: #fff;
}

.add-device-form .form-group input:focus,
.add-device-form .form-group select:focus {
  border-color: #4a5ce0;
}

.add-device-error {
  color: #e74c3c;
  font-size: 13px;
  text-align: center;
}

.add-device-submit {
  background: #4a5ce0;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  margin-top: 4px;
}

.add-device-submit:hover:not(:disabled) {
  background: #3a4cd0;
}

.add-device-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.message-actions {
  margin-top: 6px;
  padding-left: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.favorite-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #ddd;
  border-radius: 14px;
  background: #f8f8f8;
  font-size: 12px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.favorite-btn:hover {
  border-color: #ffb300;
  color: #ff8f00;
  background: #fff8e1;
}

.favorite-btn.favorited {
  border-color: #ffb300;
  color: #ff8f00;
  background: #fff8e1;
}

.history-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 20%;
  min-width: 160px;
  height: 100vh;
  z-index: 1500;
  background: rgba(232, 232, 232, 0.95);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
}

.history-panel {
  width: 100%;
  height: 100%;
  background: #e8e8e8;
  display: flex;
  flex-direction: column;
}

.history-panel-header {
  padding: 16px 16px 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ddd;
}

.new-chat-btn {
  padding: 8px 16px;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.new-chat-btn:hover {
  opacity: 0.9;
  transform: scale(1.05);
}

.mydevice-panel .history-panel-header {
  font-weight: bold;
}

.mydevice-panel .mydevice-sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.mydevice-panel .mydevice-nav-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background-color 0.2s;
}

.mydevice-panel .mydevice-nav-item:hover {
  background-color: #dcdcdc;
}

.mydevice-panel .mydevice-nav-item.active {
  background-color: #4a5ce0;
  color: white;
}

.mydevice-panel .mydevice-nav-icon {
  font-size: 18px;
}

.mydevice-panel .mydevice-sidebar-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  font-size: 14px;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px;
}

.history-item {
  padding: 12px 15px;
  margin-bottom: 8px;
  background: #f5f5f5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-item:hover {
  background: linear-gradient(90deg, #667eea 0%, #ffffff 100%);
  transform: translateX(5px);
}

.history-item.active {
  background: linear-gradient(90deg, #667eea 0%, #ffffff 100%);
  color: #333;
}

.history-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.history-item-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.history-item-time {
  font-size: 11px;
  color: #999;
  white-space: nowrap;
  margin-left: 8px;
}

.history-item.active .history-item-time {
  color: #666;
}

.delete-area {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s, background-color 0.2s;
  cursor: pointer;
  color: #ff4444;
  font-size: 14px;
  font-weight: bold;
  user-select: none;
  background: transparent;
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 60px;
}

.history-item:hover .delete-area {
  opacity: 1;
}

.delete-area:active {
  background: transparent;
}

.history-item.only-one .delete-area {
  display: none !important;
}

.chat-area {
  width: 80%;
  display: flex;
  flex-direction: column;
  background: white;
  position: relative;
}

.chat-top-bar {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: #fafafa;
  border-bottom: 1px solid #eee;
  gap: 16px;
}

.history-toggle-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e8e8e8;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.history-toggle-btn:hover {
  background: #d0d0d0;
  transform: scale(1.1);
}

.history-toggle-btn svg {
  color: #555;
}

.api-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  flex: 1;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
  transition: background-color 0.3s;
}

.status-dot.connected {
  background-color: #28a745;
  box-shadow: 0 0 6px rgba(40, 167, 69, 0.6);
}

.status-dot.disconnected {
  background-color: #dc3545;
  box-shadow: 0 0 6px rgba(220, 53, 69, 0.6);
}

.status-dot.checking {
  background-color: #ffc107;
  box-shadow: 0 0 6px rgba(255, 193, 7, 0.6);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.api-control-btn {
  padding: 4px 12px;
  border: 1px solid #999;
  border-radius: 12px;
  background: white;
  color: #333;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
  margin-left: 8px;
}

.api-control-btn:hover {
  background: #f0f0f0;
}

.api-control-btn.disconnect {
  border-color: #dc3545;
  color: #dc3545;
}

.api-control-btn.disconnect:hover {
  background: #dc3545;
  color: white;
}

.api-control-btn.connect {
  border-color: #28a745;
  color: #28a745;
}

.api-control-btn.connect:hover {
  background: #28a745;
  color: white;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border: none;
  border-radius: 24px;
  cursor: pointer;
  font-size: 14px;
  font-weight: bold;
  transition: all 0.3s;
}

.login-btn:hover {
  opacity: 0.9;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(66, 165, 245, 0.4);
}

.user-menu {
  position: relative;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  background: white;
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.user-info:hover {
  background: #f5f5f5;
  transform: translateY(-2px);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.user-avatar-placeholder {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}

.dropdown-arrow {
  color: #666;
  transition: transform 0.3s;
}

.user-menu:hover .dropdown-arrow {
  transform: rotate(180deg);
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
  overflow: hidden;
  min-width: 140px;
}

.user-dropdown button {
  width: 100%;
  padding: 12px 16px;
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;
  font-size: 14px;
  color: #333;
  transition: background 0.2s;
}

.user-dropdown button:hover {
  background: #f5f5f5;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}

.message {
  margin-bottom: 20px;
  display: flex;
  animation: fadeIn 0.3s ease;
  align-items: center;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.message.user { justify-content: flex-end; }
.message.ai { justify-content: flex-start; }
.message.system { justify-content: center; }

.message-row-user {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.recall-btn {
  opacity: 0;
  margin-top: 24px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid #ddd;
  background: #fff;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: opacity 0.15s, color 0.15s, border-color 0.15s;
}

.message-row-user:hover .recall-btn {
  opacity: 1;
}

.recall-btn:hover {
  color: #e74c3c;
  border-color: #e74c3c;
  background: #fef0f0;
}

.message-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 75%;
}

.message.user .message-wrapper {
  align-items: flex-end;
  max-width: 90%;
}

.message.ai .message-wrapper {
  align-items: flex-start;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin: 4px 0;
  text-align: center;
}

.message-content {
  padding: 14px 18px;
  border-radius: 16px;
  line-height: 1.6;
  word-wrap: break-word;
  font-size: 16px;
}

.message-content p { margin: 8px 0; }
.message-content strong { font-weight: bold; }

.message.user .message-content {
  background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
  color: #333;
  border-bottom-right-radius: 4px;
}

.message.ai .message-content {
  background: #e8e8e8;
  color: #333;
  border-bottom-left-radius: 4px;
  font-size: 16px;
}

.message.system .message-content {
  background: #fff3cd;
  color: #856404;
  font-size: 14px;
  border-radius: 8px;
}

.message-content pre {
  background: rgba(0,0,0,0.1);
  padding: 10px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-content code {
  font-family: 'Courier New', monospace;
}

.message-content pre code {
  background: none;
  padding: 0;
}

.message-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 14px;
  overflow-x: auto;
  display: block;
}

.message-content th,
.message-content td {
  border: 1px solid #ddd;
  padding: 10px 12px;
  text-align: left;
}

.message-content th {
  background: linear-gradient(180deg, #e3f2fd 0%, #ffffff 100%);
  color: #333;
  font-weight: bold;
  text-align: center;
}

.message-content tr:nth-child(even) {
  background-color: #f9f9f9;
}

.message-content tr:hover {
  background-color: #f1f1f1;
}

.message-content h3 {
  font-size: 16px;
  font-weight: bold;
  margin: 16px 0 8px 0;
  color: #333;
  border-bottom: 2px solid #667eea;
  padding-bottom: 6px;
}

.message-image {
  margin-bottom: 8px;
  max-width: 280px;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #e0e0e0;
  transition: transform 0.2s;
}

.message-image:hover {
  transform: scale(1.02);
}

.message-image img {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 12px;
}

.chat-input-area {
  padding: 20px;
  background: white;
  border-top: 1px solid #eee;
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  padding: 14px 18px;
  border: 2px solid #ddd;
  border-radius: 24px;
  font-size: 16px;
  outline: none;
  transition: border-color 0.3s;
}

.chat-input:focus {
  border-color: #667eea;
}

.send-button {
  padding: 14px 28px;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s;
  font-weight: bold;
}

.send-button:hover {
  opacity: 0.9;
  transform: scale(1.02);
}

.send-button:disabled {
  background: linear-gradient(135deg, #ef9a9a 0%, #e57373 100%);
  cursor: not-allowed;
  transform: none;
}

.stop-button {
  padding: 14px 28px;
  background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
  color: white;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.stop-button:hover {
  opacity: 0.9;
  transform: scale(1.05);
}

.upload-btn {
  padding: 14px;
  background: #f0f0f0;
  border: 2px dashed #ccc;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.upload-btn:hover {
  border-color: #667eea;
  background: #f5f0ff;
}

.device-list {
  background: white;
  border-top: 1px solid #eee;
  padding: 15px 20px;
  display: flex;
  gap: 10px;
  overflow-x: auto;
}

.device-item {
  background: #f5f5f5;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  white-space: nowrap;
}

.loading-dots {
  display: inline-flex;
  gap: 4px;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  background: #667eea;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.hidden {
  display: none;
}

.custom-device-btn {
  padding: 14px;
  background: #f0f0f0;
  border: 2px dashed #ccc;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  color: #666;
}

.custom-device-btn:hover {
  border-color: #42a5f5;
  background: #e3f2fd;
  color: #42a5f5;
}

.voice-btn {
  padding: 14px;
  background: #f0f0f0;
  border: 2px solid #ddd;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  color: #666;
  position: relative;
}

.voice-btn:hover {
  border-color: #42a5f5;
  background: #e3f2fd;
  color: #42a5f5;
}

.voice-btn.listening {
  background: #ff4444;
  border-color: #ff4444;
  color: white;
  animation: voice-pulse 1.2s ease-in-out infinite;
}

.voice-btn.listening:hover {
  background: #cc0000;
  border-color: #cc0000;
  color: white;
}

.voice-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@keyframes voice-pulse {
  0% { box-shadow: 0 0 0 0 rgba(255, 68, 68, 0.5); }
  50% { box-shadow: 0 0 0 10px rgba(255, 68, 68, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 68, 68, 0); }
}

.pref-modal-content {
  background: white;
  border-radius: 16px;
  width: 680px;
  max-width: 95%;
  overflow: hidden;
  box-shadow: 0 12px 48px rgba(0,0,0,0.25);
}

.confirm-modal-content {
  background: white;
  border-radius: 16px;
  width: 420px;
  max-width: 95%;
  overflow: hidden;
  box-shadow: 0 12px 48px rgba(0,0,0,0.25);
}

.confirm-message {
  padding: 24px 28px;
  font-size: 16px;
  color: #333;
  line-height: 1.6;
  text-align: center;
}

.confirm-actions {
  display: flex;
  border-top: 1px solid #eee;
}

.confirm-cancel-btn {
  flex: 1;
  padding: 16px;
  font-size: 15px;
  color: #666;
  background: transparent;
  border: none;
  cursor: pointer;
  border-right: 1px solid #eee;
  transition: background 0.2s;
}

.confirm-cancel-btn:hover {
  background: #f5f5f5;
}

.confirm-ok-btn {
  flex: 1;
  padding: 16px;
  font-size: 15px;
  color: #fff;
  background: #007bff;
  border: none;
  cursor: pointer;
  transition: background 0.2s;
}

.confirm-ok-btn:hover {
  background: #0069d9;
}

.pref-form {
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-height: 75vh;
  overflow-y: auto;
}

.pref-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pref-label {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.price-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.price-input {
  width: 130px;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  text-align: center;
  outline: none;
  transition: border-color 0.2s;
}

.price-input:focus {
  border-color: #64b5f6;
  box-shadow: 0 0 0 2px rgba(100, 181, 246, 0.2);
}

.price-input::-webkit-inner-spin-button {
  opacity: 1;
  height: 28px;
}

.price-separator {
  font-size: 16px;
  color: #999;
  font-weight: 500;
}

.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tag {
  padding: 10px 18px;
  border: 1px solid #ddd;
  border-radius: 22px;
  font-size: 14px;
  cursor: pointer;
  background: #f5f5f5;
  color: #555;
  transition: all 0.2s;
  user-select: none;
}

.tag:hover {
  border-color: #64b5f6;
  background: #e3f2fd;
  color: #1976d2;
}

.tag.active {
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border-color: #42a5f5;
}

.range-slider {
  position: relative;
  height: 44px;
  display: flex;
  align-items: center;
  margin: 8px 0;
}

.range-track {
  position: absolute;
  width: 100%;
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
}

.range-fill {
  position: absolute;
  height: 8px;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  border-radius: 4px;
  transition: left 0.15s, right 0.15s;
}

.range-slider input[type="range"] {
  position: absolute;
  width: 100%;
  height: 8px;
  -webkit-appearance: none;
  appearance: none;
  background: transparent;
  border-radius: 4px;
  outline: none;
  pointer-events: none;
}

.range-slider input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  cursor: pointer;
  pointer-events: auto;
  border: 3px solid white;
  box-shadow: 0 3px 8px rgba(0,0,0,0.3);
  transition: transform 0.2s, box-shadow 0.2s;
}

.range-slider input[type="range"]::-webkit-slider-thumb:hover {
  transform: scale(1.15);
  box-shadow: 0 4px 12px rgba(66, 165, 245, 0.4);
}

.range-slider input[type="range"]::-moz-range-thumb {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  cursor: pointer;
  pointer-events: auto;
  border: 3px solid white;
  box-shadow: 0 3px 8px rgba(0,0,0,0.3);
  transition: transform 0.2s, box-shadow 0.2s;
}

.range-slider input[type="range"]::-moz-range-thumb:hover {
  transform: scale(1.15);
  box-shadow: 0 4px 12px rgba(66, 165, 245, 0.4);
}

.range-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.pref-submit {
  padding: 14px;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  transition: opacity 0.3s;
  margin-top: 4px;
}

.pref-submit:hover:not(:disabled) {
  opacity: 0.9;
}

.pref-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-history {
  text-align: center;
  color: #999;
  padding: 40px 20px;
  font-size: 14px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 100;
}

.auth-modal-content {
  background: white;
  border-radius: 12px;
  width: 400px;
  max-width: 90%;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0,0,0,0.2);
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 400px;
  max-width: 90%;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0,0,0,0.2);
}

.modal-header {
  padding: 20px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h2 {
  font-size: 18px;
  color: #333;
}

.modal-close {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #999;
  padding: 0;
  line-height: 1;
}

.modal-close:hover {
  color: #333;
}

.auth-form {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.form-group input {
  padding: 12px 14px;
  border: 2px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.form-group input:focus {
  border-color: #667eea;
}

.form-group input:disabled {
  background: #f5f5f5;
  color: #999;
}

.form-hint {
  font-size: 12px;
  color: #999;
  margin-top: -8px;
}

.auth-submit {
  padding: 14px;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  transition: opacity 0.3s;
}

.auth-submit:hover:not(:disabled) {
  opacity: 0.9;
}

.auth-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-switch {
  text-align: center;
  margin-top: 15px;
  font-size: 14px;
  color: #666;
}

.auth-link {
  color: #42a5f5;
  cursor: pointer;
  font-weight: 500;
  margin-left: 5px;
}

.auth-link:hover {
  text-decoration: underline;
}

.error-message {
  color: #dc3545;
  font-size: 13px;
  text-align: center;
  margin-top: 10px;
}

.profile-form {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.profile-submit {
  padding: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  transition: opacity 0.3s;
}

.profile-submit:hover:not(:disabled) {
  opacity: 0.9;
}

.profile-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px 20px;
}

.welcome-title {
  font-size: 32px;
  font-weight: bold;
  color: #000;
  margin-bottom: 40px;
}

.suggestion-buttons {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 900px;
}

.suggestion-row {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
}

.suggestion-btn {
  background: #f5f5f5;
  border: none;
  padding: 14px 20px;
  border-radius: 16px;
  font-size: 15px;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
  max-width: 45%;
  white-space: nowrap;
}

.suggestion-btn:hover {
  background: #e8e8e8;
  transform: translateY(-2px);
}
.settings-modal-content {
  background: white;
  border-radius: 16px;
  width: 720px;
  height: 560px;
  display: flex;
  flex-direction: row;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
  position: relative;
}

.settings-left-nav {
  width: 180px;
  min-width: 180px;
  background: #f8f9fa;
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  border-right: 1px solid #eee;
}

.settings-nav-header {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin-bottom: 24px;
  padding: 0 12px;
}

.settings-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  transition: all 0.2s;
  user-select: none;
  margin-bottom: 4px;
}

.settings-nav-item:hover {
  background: #e8e8e8;
  color: #333;
}

.settings-nav-item.active {
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
}

.settings-close-btn {
  position: absolute;
  top: 12px;
  right: 16px;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: #999;
  font-size: 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  z-index: 100;
}

.settings-close-btn:hover {
  background: #f5f5f5;
  color: #333;
}

.settings-right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.settings-panel-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  padding: 24px 32px 0 32px;
  margin-bottom: 8px;
}

.settings-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 32px 32px 32px;
}

.settings-panel-body .avatar-upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
  gap: 10px;
}

.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s;
}

.avatar-preview:hover {
  transform: scale(1.05);
}

.profile-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: #999;
  background: #d6d6d6;
}

.avatar-upload-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 30px;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-preview:hover .avatar-upload-overlay {
  opacity: 1;
}

.avatar-hint {
  font-size: 12px;
  color: #999;
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-form .form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.settings-form .form-group label {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.settings-form .form-group input,
.settings-form .form-group select {
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  background: white;
  width: 100%;
}

.settings-form .form-group input:focus,
.settings-form .form-group select:focus {
  border-color: #42a5f5;
}

.settings-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.preference-section {
  padding-bottom: 20px;
}

.preference-hint {
  font-size: 13px;
  color: #888;
  margin: -8px 0 16px 0;
  line-height: 1.5;
}

.auto-preference-badge {
  background: #e3f2fd;
  border: 1px solid #90caf9;
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #1565c0;
  line-height: 1.6;
}

.auto-preference-badge strong {
  color: #0d47a1;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .form-group {
  flex: 1;
}

.settings-save-btn {
  padding: 10px 28px;
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s;
  font-weight: 500;
}

.settings-save-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.settings-save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.success-message {
  background: #e8f5e9;
  color: #2e7d32;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 13px;
  text-align: center;
}

.security-section {
  margin-bottom: 28px;
}

.security-section h3 {
  font-size: 16px;
  color: #333;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.danger-section {
  border: 1px solid #ffcdd2;
  border-radius: 12px;
  padding: 20px;
  background: #fff5f5;
}

.danger-section h3 {
  color: #c62828;
  border-bottom-color: #ffcdd2;
}

.danger-hint {
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
  line-height: 1.6;
}

.delete-account-btn {
  padding: 10px 28px;
  background: #e53935;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
  font-weight: 500;
}

.delete-account-btn:hover:not(:disabled) {
  background: #c62828;
}

.delete-account-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-delete-btn {
  padding: 10px 28px;
  background: #f5f5f5;
  color: #666;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.cancel-delete-btn:hover {
  background: #e8e8e8;
}

</style>