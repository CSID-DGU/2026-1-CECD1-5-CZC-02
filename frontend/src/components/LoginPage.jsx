import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Wind } from 'lucide-react';
import { api } from '../api/client';

export function LoginPage() {
  const navigate = useNavigate();
  const [isSignup, setIsSignup] = useState(false);
  const [formData, setFormData] = useState({
    email: '',
    name: '',
    password: '',
    passwordConfirm: ''
  });
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (isSignup && formData.password !== formData.passwordConfirm) {
      setErrorMessage('비밀번호가 일치하지 않습니다.');
      return;
    }

    try {
      setIsSubmitting(true);

      const response = isSignup
        ? await api.post('/api/auth/signup', {
            email: formData.email,
            password: formData.password,
            name: formData.name,
          })
        : await api.post('/api/auth/login', {
            email: formData.email,
            password: formData.password,
          });

      const accessToken = response.data?.data?.accessToken;

      if (!accessToken) {
        throw new Error('토큰이 응답에 포함되지 않았습니다.');
      }

      localStorage.setItem('accessToken', accessToken);
      navigate('/dashboard');
    } catch (error) {
      const message = error.response?.data?.message || '요청 처리 중 오류가 발생했습니다.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleMode = () => {
    setIsSignup(!isSignup);
    setErrorMessage('');
    setFormData({
      email: '',
      name: '',
      password: '',
      passwordConfirm: ''
    });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-b from-[#F6F8FC] to-[#EEF2F9]">
      <div className="flex w-full max-w-5xl overflow-hidden rounded-3xl border border-white/60 bg-white/70 shadow-[0_20px_60px_-24px_rgba(37,99,235,0.4)] backdrop-blur">
        {/* Left: Brand Area */}
        <div className="flex-1 bg-gradient-to-br from-blue-50 to-white flex items-center justify-center p-12">
          <div className="text-center max-w-md">
            <div className="border border-blue-100 rounded-2xl px-16 py-12 mb-8 bg-white/80 shadow-sm min-h-[285px] flex flex-col items-center justify-center">
              <div className="flex items-center justify-center gap-3 mb-6">
                <Wind className="w-12 h-12 text-blue-500" strokeWidth={2.5} />
                <span className="text-5xl font-extrabold tracking-tight text-gray-900" style={{ fontFamily: 'var(--display)' }}>
                  Sales<span className="text-blue-600">Breeze</span>
                </span>
              </div>
              <p className="text-xl text-gray-600">AI 영업 자동화 에이전트</p>
            </div>
            <p className="text-gray-500">
              AI 기반 영업 활동 분석 및<br />
              의사결정 지원 시스템
            </p>
          </div>
        </div>

        {/* Right: Login Form */}
        <div className="flex-1 bg-white/60 flex items-center justify-center p-12">
          <div className="w-full max-w-md">
            <div className="bg-white border border-slate-200 rounded-2xl shadow-sm p-8">
              <h2 className="text-2xl font-bold text-slate-900 mb-8 text-center" style={{ fontFamily: 'var(--display)', color: '#0f172a', fontWeight: 700 }}>
                {isSignup ? '회원가입' : '로그인'}
              </h2>

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm text-gray-700 mb-2">이메일</label>
                  <input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-400 text-sm"
                    placeholder="이메일을 입력하세요"
                    autoComplete="email"
                  />
                </div>

                {isSignup && (
                  <div>
                    <label className="block text-sm text-gray-700 mb-2">이름</label>
                    <input
                      type="text"
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-400 text-sm"
                      placeholder="이름을 입력하세요"
                      autoComplete="name"
                    />
                  </div>
                )}

                <div>
                  <label className="block text-sm text-gray-700 mb-2">비밀번호</label>
                  <input
                    type="password"
                    value={formData.password}
                    onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-400 text-sm"
                    placeholder="비밀번호를 입력하세요"
                    autoComplete={isSignup ? 'new-password' : 'current-password'}
                  />
                </div>

                {isSignup && (
                  <div>
                    <label className="block text-sm text-gray-700 mb-2">비밀번호 확인</label>
                    <input
                      type="password"
                      value={formData.passwordConfirm}
                      onChange={(e) => setFormData({ ...formData, passwordConfirm: e.target.value })}
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-400 text-sm"
                      placeholder="비밀번호를 다시 입력하세요"
                      autoComplete="new-password"
                    />
                  </div>
                )}

                {errorMessage && (
                  <p className="text-sm text-red-600">{errorMessage}</p>
                )}

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full bg-gradient-to-r from-sky-500 to-indigo-500 hover:from-sky-600 hover:to-indigo-600 disabled:opacity-50 text-white font-semibold py-2.5 rounded-lg shadow-sm transition text-sm"
                >
                  {isSubmitting ? '처리 중...' : isSignup ? '회원가입' : '로그인'}
                </button>
              </form>

              <div className="mt-6 text-center">
                <button
                  onClick={toggleMode}
                  className="text-xs text-gray-600 hover:text-gray-800"
                >
                  {isSignup ? '이미 계정이 있으신가요? 로그인' : '계정이 없으신가요? 회원가입'}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
