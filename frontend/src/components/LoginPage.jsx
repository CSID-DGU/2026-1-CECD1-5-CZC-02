import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import logoImg from '../assets/님버스테크 로고.png';

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
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="flex w-full max-w-5xl bg-gray-50">
        {/* Left: Brand Area */}
        <div className="flex-1 bg-gradient-to-br from-blue-50 to-white flex items-center justify-center p-12">
          <div className="text-center max-w-md">
            <div className="border-2 border-blue-200 rounded-2xl px-16 py-12 mb-8 bg-white min-h-[285px] flex flex-col items-center justify-center">
              <div className="flex flex-col items-center mb-6">
                {}
                {}
                <img src={logoImg} alt="NimbusTech" className="w-96 h-auto object-contain" />
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
        <div className="flex-1 bg-white flex items-center justify-center p-12">
          <div className="w-full max-w-md">
            <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-8">
              <h2 className="text-xl !font-bold !text-black mb-8 text-center">
                {isSignup ? '회원가입' : '로그인'}
              </h2>

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm text-gray-700 mb-2">이메일</label>
                  <input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
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
                      className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
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
                    className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
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
                      className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
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
                  className="w-full bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white py-2.5 rounded text-sm"
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
