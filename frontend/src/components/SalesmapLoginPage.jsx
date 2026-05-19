import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

export function SalesmapLoginPage() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    accountId: '',
    password: ''
  });

  const handleSubmit = (e) => {
    e.preventDefault();

    localStorage.setItem('salesmapConnected', 'true');
    localStorage.setItem('salesmapCompany', '(주)님버스테크');

    navigate('/dashboard');
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-6">
      <div className="w-full max-w-md">
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-sm text-gray-600 hover:text-gray-800 mb-6"
        >
          <ArrowLeft className="w-4 h-4" />
          뒤로 가기
        </button>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-8">
          <h2 className="text-xl text-gray-800 mb-2">Salesmap 계정 연동</h2>
          <p className="text-sm text-gray-500 mb-6">
            Salesmap 계정 정보를 입력하여 시스템을 연동하세요
          </p>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm text-gray-700 mb-2">계정 ID</label>
              <input
                type="text"
                value={formData.accountId}
                onChange={(e) => setFormData({ ...formData, accountId: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
                placeholder="Salesmap 계정 ID를 입력하세요"
                required
              />
            </div>

            <div>
              <label className="block text-sm text-gray-700 mb-2">비밀번호</label>
              <input
                type="password"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
                placeholder="비밀번호를 입력하세요"
                required
              />
            </div>

            <div className="bg-blue-50 border border-blue-200 rounded-md p-3">
              <p className="text-xs text-blue-700">
                연동 후 Salesmap의 데이터를 자동으로 가져와 AI 분석을 수행합니다.
              </p>
            </div>

            <button
              type="submit"
              className="w-full bg-blue-500 hover:bg-blue-600 text-white py-2.5 rounded text-sm"
            >
              연동하기
            </button>
          </form>

          <div className="mt-6 text-center">
            <a href="#" className="text-xs text-gray-600 hover:text-gray-800">
              Salesmap 계정이 없으신가요?
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
